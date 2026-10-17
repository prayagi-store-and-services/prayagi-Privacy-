package com.example.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Tells which app is actually open on the screen, using Android's Usage access switch.
 * Usage access is a system setting the user turns on by hand; it never sends anything anywhere -
 * the events are read on the phone only. When the switch is off, every answer is "unknown" (null),
 * never a guess.
 */
object ForegroundAppDetector {

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Package name of the app that most recently moved to the foreground, or null when Usage
     * access is off or the system reports nothing. Null must always be treated as "do not know",
     * never as "background".
     */
    fun foregroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 10 * 60 * 1000L, now) ?: return null
            val event = UsageEvents.Event()
            var last: String? = null
            var lastTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isFg = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (isFg && event.timeStamp >= lastTime) {
                    lastTime = event.timeStamp
                    last = event.packageName
                }
            }
            last
        } catch (e: Exception) {
            null
        }
    }
}
