package com.example.siteblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Optional "security patch" style update for the block list. The list is data, so it can arrive without an app update.
 * It is OFF until the user turns it on. When on, Android runs it about once a day when the phone is online
 * (Android may delay it in battery saver). It uses the same checked download as the "Update lists now" button.
 */
object AutoListUpdate {
    const val WORK = "siteblock_list_update"

    fun setOn(context: Context, on: Boolean) {
        val prefs = SiteBlockPrefs(context)
        prefs.autoUpdate = on
        val wm = WorkManager.getInstance(context.applicationContext)
        if (on) {
            val req = PeriodicWorkRequestBuilder<AutoListUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** What the screen says about the automatic update. Pure, unit tested. Times are epoch milliseconds, 0 = never. */
    fun statusText(on: Boolean, lastUpdatedMs: Long, lastTryMs: Long, lastFailure: String?, fmt: (Long) -> String): String {
        if (!on) return "Automatic update is off. The built-in list stays as it is until you tap the button."
        val tried = if (lastTryMs > 0L) "Last automatic try: " + fmt(lastTryMs) + ". " else "Has not run yet (Android picks the time, about once a day). "
        val result = when {
            lastTryMs == 0L -> ""
            lastFailure != null -> "It did not work: " + lastFailure
            else -> "It worked."
        }
        val using = if (lastUpdatedMs > 0L) " Using a list from " + fmt(lastUpdatedMs) + "." else " Using the built-in list."
        return tried + result + using
    }
}

class AutoListUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = SiteBlockPrefs(applicationContext)
        if (!prefs.autoUpdate) return Result.success()
        val r = BlocklistUpdater.update(applicationContext)
        prefs.autoLastTry = System.currentTimeMillis()
        prefs.autoLastFailure = when (r) {
            is BlocklistUpdater.Result.Ok -> null
            is BlocklistUpdater.Result.Failed -> r.reason
        }
        // The new list is used the next time blocking starts. A failure keeps the old list; the next daily run tries again.
        return Result.success()
    }
}
