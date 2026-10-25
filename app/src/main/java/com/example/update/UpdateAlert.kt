package com.example.update

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Update alert: every 6 hours (network required) asks the repo's latest release whether a newer version exists and
 * posts one metadata-only notification per new version. Download and Install each require a separate choice.
 */
object UpdateAlert {
    const val EXTRA_AUTO_UPDATE = "netra_auto_update"
    private const val CHANNEL_ID = "netra_app_update"
    private const val NOTIFICATION_ID = 7431
    private const val WORK_NAME = "netra_update_alert"
    private const val PREFS = "netra_update_alert"

    fun start(activity: Activity) {
        try { schedule(activity.applicationContext) } catch (_: Exception) { /* background check unavailable; the manual check still works */ }
        handle(activity, activity.intent)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateAlertWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Notification entry offers Download first, then a separate verified Install action. */
    fun handle(activity: Activity, intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_UPDATE, false) != true) return
        intent.removeExtra(EXTRA_AUTO_UPDATE)
        android.app.AlertDialog.Builder(activity)
            .setTitle("Download SensorGuard update?")
            .setMessage("Download the update from SensorGuard's official GitHub release. Android will not open its installer until you choose Install after verification. Keep Play Protect enabled; do not bypass a security warning.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Download") { _,_ ->
                Thread {
                    try {
                        val result=AppUpdater.checkNow(activity)
                        check(result is AppUpdater.CheckResult.Newer) { "No newer update available." }
                        val file=AppUpdater.download(activity,result.release)
                        activity.runOnUiThread {
                            if(!activity.isFinishing) android.app.AlertDialog.Builder(activity)
                                .setTitle("Update downloaded and verified")
                                .setMessage("Install opens Android's confirmation. You can choose Not now without installing.")
                                .setNegativeButton("Not now",null)
                                .setPositiveButton("Install") { _,_ ->
                                    Thread {
                                        try { AppUpdater.install(activity,file) }
                                        catch(e:Exception) { activity.runOnUiThread { Toast.makeText(activity,e.message ?: "Update refused.",Toast.LENGTH_LONG).show() } }
                                    }.start()
                                }.show()
                        }
                    } catch(e:Exception) { activity.runOnUiThread { Toast.makeText(activity,e.message ?: "Update unavailable.",Toast.LENGTH_LONG).show() } }
                }.start()
            }.show()
    }

    internal fun notifyIfNew(c: Context, release: AppRelease, appName: String, downloaded: Boolean = false) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (release.versionCode <= prefs.getLong("notified_code", 0L)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName) ?: return
        launch.putExtra(EXTRA_AUTO_UPDATE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update available")
            .setContentText("Version " + release.versionName + " is available. Tap to choose Download.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        prefs.edit().putLong("notified_code", release.versionCode).apply()
    }
}

class UpdateAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val r = AppUpdater.checkNow(applicationContext)
            if (r is AppUpdater.CheckResult.Newer) {
                // Notify about metadata only. APK download needs an explicit choice.
                val saved=false
                UpdateAlert.notifyIfNew(applicationContext, r.release, "", saved)
            }
        } catch (_: Exception) {
            // Try again at the next run.
        }
        Result.success()
    }
}
