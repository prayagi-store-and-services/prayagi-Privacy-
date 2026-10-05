package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.LiveGuardState
import com.example.data.model.ProtectionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the guard service last reported, saved on this phone so the widget can show it without any background work. */
data class GuardSnapshot(val status: String, val micLocked: Boolean, val camBlocked: Boolean, val adminActive: Boolean, val atMs: Long)

object SgWidgetStore {
    private const val PREFS = "sg_widget_snapshot"

    fun save(context: Context, s: LiveGuardState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("status", s.status.name).putBoolean("mic", s.isMicLocked).putBoolean("cam", s.isCamBlocked)
            .putBoolean("admin", s.isDeviceAdminActive).putLong("at", System.currentTimeMillis()).apply()
    }

    fun load(context: Context): GuardSnapshot? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val st = p.getString("status", null) ?: return null
        return GuardSnapshot(st, p.getBoolean("mic", false), p.getBoolean("cam", false), p.getBoolean("admin", false), p.getLong("at", 0L))
    }

    fun statusLabel(name: String): String = when (name) {
        ProtectionStatus.PROTECTED.name -> "Protected"
        ProtectionStatus.MONITORING_IDLE.name -> "Monitoring (screen on)"
        ProtectionStatus.EXCEPTION_CALL.name -> "Paused for a call"
        ProtectionStatus.PARTIALLY_PROTECTED.name -> "Partly protected"
        ProtectionStatus.DISABLED.name -> "Guard is off"
        else -> "Unavailable"
    }
}

/**
 * SensorGuard widget: the guard status, microphone lock, camera block and device admin state exactly as the guard
 * service last reported them, with the time. No snapshot yet shows "Unavailable". No timer, no background work.
 */
class SgWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) draw(context, appWidgetManager, id)
    }

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            for (id in mgr.getAppWidgetIds(ComponentName(context, SgWidgetProvider::class.java))) draw(context, mgr, id)
        }

        private fun draw(context: Context, mgr: AppWidgetManager, id: Int) {
            val snap = SgWidgetStore.load(context)
            val v = RemoteViews(context.packageName, R.layout.widget_sg)
            if (snap == null) {
                v.setTextViewText(R.id.sg_status, "Unavailable")
                v.setTextViewText(R.id.sg_mic, "Microphone: Unavailable")
                v.setTextViewText(R.id.sg_cam, "Camera: Unavailable")
                v.setTextViewText(R.id.sg_admin, "Device admin: Unavailable")
                v.setTextViewText(R.id.sg_time, "Open SensorGuard once to start")
            } else {
                v.setTextViewText(R.id.sg_status, SgWidgetStore.statusLabel(snap.status))
                v.setTextViewText(R.id.sg_mic, "Microphone: " + if (snap.micLocked) "Locked" else "Not locked")
                v.setTextViewText(R.id.sg_cam, "Camera: " + if (snap.camBlocked) "Blocked" else "Not blocked")
                v.setTextViewText(R.id.sg_admin, "Device admin: " + if (snap.adminActive) "Active" else "Not active")
                v.setTextViewText(R.id.sg_time, "As reported by the guard at " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(snap.atMs)))
            }
            val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            v.setOnClickPendingIntent(R.id.sg_root, PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            mgr.updateAppWidget(id, v)
        }
    }
}
