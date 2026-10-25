package com.example.update

import android.content.Context
import java.io.File

/** No APK downloader or installer remains. Eco owns app downloads and updates. */
object AppUpdater {
    const val ECO_PACKAGE = "com.prayagi.netraeco"
    internal val LEGACY_WORK_NAMES = listOf("netra_update_alert")
    fun retireLegacyUpdater(context: Context) {
        val c=context.applicationContext
        val wm=androidx.work.WorkManager.getInstance(c)
        LEGACY_WORK_NAMES.forEach { wm.cancelUniqueWork(it) }
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(7431)
        c.getSharedPreferences("netra_update_alert",Context.MODE_PRIVATE).edit().clear().apply()
        c.getSharedPreferences("verified_update_artifacts",Context.MODE_PRIVATE).edit().clear().apply()
        Thread { File(c.cacheDir,"updates").deleteRecursively() }.start()
    }
    fun openEco(context: Context): Boolean {
        val launch=context.packageManager.getLaunchIntentForPackage(ECO_PACKAGE) ?: return false
        context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
