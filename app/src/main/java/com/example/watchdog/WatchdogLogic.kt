package com.example.watchdog

/**
 * Honest wording and throttling for the mic/camera watchdog.
 * A normal app cannot be told WHICH app used the mic or camera, so the text says "Unavailable" unless the OS gave a name.
 */
object WatchdogLogic {
    const val THROTTLE_MS = 60_000L

    fun appText(name: String?): String = if (name.isNullOrBlank()) "Unavailable" else name

    /** Only the suspicious case gets a loud alert: the screen is off and no phone call explains the mic. */
    fun isSuspicious(screenOn: Boolean, onCall: Boolean): Boolean = !screenOn && !onCall

    fun title(sensor: String, screenOn: Boolean): String =
        if (screenOn) "$sensor in use" else "$sensor in use while the screen is off"

    fun body(sensor: String, appName: String?): String =
        "A $sensor session was detected. Which app: ${appText(appName)}. " +
            "Android cannot always say which app, so this is a signal, not proof. Check the green dot / Privacy dashboard in Android settings."

    /** One alert per sensor key per minute. Returns true when an alert may be sent. */
    class Throttle(private val windowMs: Long = THROTTLE_MS) {
        private val last = HashMap<String, Long>()
        fun allow(key: String, nowMs: Long): Boolean {
            val prev = last[key]
            if (prev != null && nowMs - prev < windowMs) return false
            last[key] = nowMs
            return true
        }
    }
}
