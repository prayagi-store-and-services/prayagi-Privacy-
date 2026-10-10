package com.example.siteblock

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Automatic block list update. It is always on and has no off switch: the list is data, so protection can improve
 * without a new app version. On Wi-Fi (or any unmetered network) it runs by itself about every 12 hours.
 * On mobile data it runs at the interval the user picks (default 24 hours). Both use the same checked download
 * as the "Update lists now" button. Android may delay a run in battery saver.
 */
object AutoListUpdate {
    const val WIFI_WORK = "siteblock_list_update_wifi"
    const val MOBILE_WORK = "siteblock_list_update_mobile"
    const val WIFI_HOURS = 12
    const val DEFAULT_MOBILE_HOURS = 24
    val MOBILE_CHOICES = listOf(6, 12, 24, 48, 72)
    const val KEY_KIND = "kind"

    /** Only the listed choices are valid. Anything else falls back to the default. Pure, unit tested. */
    fun cleanMobileHours(h: Int): Int = if (h in MOBILE_CHOICES) h else DEFAULT_MOBILE_HOURS

    /** Schedules both automatic updates. Safe to call again at any time. */
    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val wifi = PeriodicWorkRequestBuilder<AutoListUpdateWorker>(WIFI_HOURS.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setInputData(workDataOf(KEY_KIND to "wifi"))
            .build()
        wm.enqueueUniquePeriodicWork(WIFI_WORK, ExistingPeriodicWorkPolicy.KEEP, wifi)
        scheduleMobile(context, SiteBlockPrefs(context).mobileHours, ExistingPeriodicWorkPolicy.KEEP)
    }

    /** The user picked a new mobile data interval. */
    fun setMobileHours(context: Context, hours: Int) {
        val h = cleanMobileHours(hours)
        SiteBlockPrefs(context).mobileHours = h
        scheduleMobile(context, h, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun scheduleMobile(context: Context, hours: Int, policy: ExistingPeriodicWorkPolicy) {
        val req = PeriodicWorkRequestBuilder<AutoListUpdateWorker>(cleanMobileHours(hours).toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_KIND to "mobile"))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(MOBILE_WORK, policy, req)
    }

    /** What the screen says about the automatic update. Pure, unit tested. Times are epoch milliseconds, 0 = never. */
    fun statusText(mobileHours: Int, lastUpdatedMs: Long, lastTryMs: Long, lastFailure: String?, fmt: (Long) -> String): String {
        val plan = "On Wi-Fi the list updates by itself about every " + WIFI_HOURS + " hours. On mobile data it updates about every " +
            cleanMobileHours(mobileHours) + " hours. "
        val tried = if (lastTryMs > 0L) "Last automatic try: " + fmt(lastTryMs) + ". " else "Has not run yet (Android picks the exact time). "
        val result = when {
            lastTryMs == 0L -> ""
            lastFailure != null -> "It did not work: " + lastFailure
            else -> "It worked."
        }
        val using = if (lastUpdatedMs > 0L) " Using a list from " + fmt(lastUpdatedMs) + "." else " Using the built-in list."
        return plan + tried + result + using
    }
}

class AutoListUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(AutoListUpdate.KEY_KIND) ?: "mobile"
        if (kind == "mobile") {
            // On Wi-Fi the Wi-Fi job already does the update, so the mobile data timer does not use data twice.
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null && !cm.isActiveNetworkMetered) return Result.success()
        }
        val prefs = SiteBlockPrefs(applicationContext)
        val r = BlocklistUpdater.update(applicationContext)
        prefs.autoLastTry = System.currentTimeMillis()
        prefs.autoLastFailure = when (r) {
            is BlocklistUpdater.Result.Ok -> null
            is BlocklistUpdater.Result.Failed -> r.reason
        }
        // The new list is used the next time blocking starts. A failure keeps the old list; the next run tries again.
        return Result.success()
    }
}
