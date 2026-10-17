package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.SensorGuardApplication

/**
 * Level 1 background-mic guard. When the microphone is in use by an app that is NOT the app open
 * on the screen, shows an instant alert naming that app. Tapping the alert opens that app's own
 * system settings page, where its microphone permission can be revoked with one more tap.
 *
 * Honest limits: this detects and names; it cannot block the microphone for other apps (no normal
 * app can - that needs root or a Shizuku setup). If Usage access is off or the foreground app is
 * unknown, no alert is shown - the feature never guesses. Phone-call and system packages never
 * alert. One alert per app per cooldown window at most. Nothing leaves the phone.
 */
class BackgroundMicAlerter(private val context: Context) {

    private val lastAlertAtMs = mutableMapOf<String, Long>()

    fun maybeAlert(pkg: String, appLabel: String) {
        val now = System.currentTimeMillis()
        val foreground = ForegroundAppDetector.foregroundPackage(context) ?: return
        if (!shouldAlert(pkg, foreground, lastAlertAtMs[pkg] ?: 0L, now)) return
        lastAlertAtMs[pkg] = now
        postNotification(pkg, appLabel)
    }

    private fun postNotification(pkg: String, appLabel: String) {
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", pkg, null)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            pkg.hashCode(),
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, SensorGuardApplication.CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Background microphone use")
            .setContentText("$appLabel is using the mic while it is not on your screen. Tap to review its permission.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$appLabel is using the mic while it is not on your screen. Tap to open its system page, where you can revoke the Microphone permission."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .setContentIntent(settingsPendingIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALERT_ID_BASE + (pkg.hashCode() and 0x0FFF), notification)
    }

    companion object {
        private const val ALERT_ID_BASE = 3000
        const val COOLDOWN_MS = 10 * 60 * 1000L

        /** Calls and system UI use the mic in the background by design; alerting on them would be noise. */
        private val NEVER_ALERT = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui"
        )

        /** Pure decision so it can be unit tested. foregroundPkg null means "unknown", which never alerts. */
        fun shouldAlert(pkg: String, foregroundPkg: String?, lastAlertAtMs: Long, nowMs: Long): Boolean {
            if (foregroundPkg == null) return false
            if (pkg == foregroundPkg) return false
            if (pkg in NEVER_ALERT) return false
            return nowMs - lastAlertAtMs >= COOLDOWN_MS
        }
    }
}
