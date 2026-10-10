package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.ui.theme.RedesignGate

/** The app name shown inside the app. Switches with the date gate (11 Oct 2026 00:00 device clock). */
object Brand {
    const val OLD_NAME = "SensorGuard"
    const val NEW_NAME = "Netra Privacy Guard"

    fun nameAt(nowMillis: Long): String = if (RedesignGate.isOn(nowMillis)) NEW_NAME else OLD_NAME

    val name: String get() = nameAt(System.currentTimeMillis())

    /** Shown only after the gate. */
    fun rePinNote(nowMillis: Long): String? =
        if (RedesignGate.isOn(nowMillis)) "The app name changed. If the icon on your home screen disappeared, add it again from your app list." else null

    private const val OLD_LAUNCHER = "com.example.MainActivity"
    private const val NEW_LAUNCHER = "com.example.NetraMainActivity"

    /**
     * After the gate, the launcher entry with the new name is switched on and the old one off. Safe to call any number of times.
     * The package, application id and all app data stay the same. Some launchers drop an old home-screen shortcut when this happens.
     */
    fun applyLauncherName(context: Context) {
        if (!RedesignGate.isOn()) return
        val pm = context.packageManager
        val neu = ComponentName(context.packageName, NEW_LAUNCHER)
        val old = ComponentName(context.packageName, OLD_LAUNCHER)
        if (pm.getComponentEnabledSetting(neu) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return
        pm.setComponentEnabledSetting(neu, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(old, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
