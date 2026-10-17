package com.example.service

import android.content.Context
import android.util.Log

/**
 * Holds the on/off state and the set of apps currently silenced by Level 2, and applies the
 * appops changes through Shizuku. State lives in its own SharedPreferences so it survives a
 * service restart: if the guard stops while apps are silenced, the next start can still see
 * (and restore) them.
 *
 * Everything happens on this phone. Blocking work runs off the main thread (shell calls block).
 */
class MicBlockController(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ignored = mutableSetOf<String>().apply {
        addAll(prefs.getStringSet(KEY_IGNORED, emptySet()) ?: emptySet())
    }

    @Volatile
    var isEnabled: Boolean = prefs.getBoolean(KEY_ENABLED, false)
        private set

    fun setEnabled(on: Boolean) {
        isEnabled = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) restoreAll()
    }

    fun ignoredPackages(): Set<String> = synchronized(ignored) { ignored.toSet() }

    /**
     * Reacts to mic activity: silences background mic users and restores whichever blocked app
     * is now in the foreground. Pass an empty [micActivePkgs] for a pure reconcile pass.
     */
    fun onMicEvent(micActivePkgs: Set<String>, foregroundPkg: String?) {
        if (!isEnabled) return
        val (toIgnore, toAllow) = ShizukuEnforcer.decideMicModes(
            micActivePkgs, foregroundPkg, ignoredPackages()
        )
        apply(toIgnore, toAllow)
    }

    private fun apply(toIgnore: Set<String>, toAllow: Set<String>) {
        if (toIgnore.isEmpty() && toAllow.isEmpty()) return
        val commands = toIgnore.map { it to false } + toAllow.map { it to true }
        if (!ShizukuEnforcer.setMicModes(commands)) {
            Log.w(TAG, "appops update failed (Shizuku not running or permission missing)")
            return
        }
        synchronized(ignored) {
            ignored.addAll(toIgnore)
            ignored.removeAll(toAllow)
            prefs.edit().putStringSet(KEY_IGNORED, ignored.toSet()).apply()
        }
    }

    /** Restores the microphone for every app this controller silenced. */
    fun restoreAll() {
        val all = ignoredPackages()
        if (all.isEmpty()) return
        if (ShizukuEnforcer.setMicModes(all.map { it to true })) {
            synchronized(ignored) {
                ignored.clear()
                prefs.edit().remove(KEY_IGNORED).apply()
            }
        } else {
            Log.w(TAG, "restoreAll failed; will retry on next start")
        }
    }

    companion object {
        private const val TAG = "MicBlockController"
        private const val PREFS_NAME = "mic_block_state"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_IGNORED = "ignored_pkgs"
    }
}
