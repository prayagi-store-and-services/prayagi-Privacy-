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
        // Per-package verified results only: a package enters the silenced set when its ignore is
        // confirmed, and leaves it only when its own restore is confirmed. Anything unconfirmed
        // stays exactly as it was, so a later pass can retry it.
        val results = ShizukuEnforcer.setMicModesVerified(
            toIgnore.map { it to false } + toAllow.map { it to true }
        )
        val ignoreSucceeded = toIgnore.filter { results[it] == true }.toSet()
        val restoreSucceeded = toAllow.filter { results[it] == true }.toSet()
        if (results.size < toIgnore.size + toAllow.size || results.containsValue(false)) {
            Log.w(TAG, "partial appops batch: ${results.size}/${toIgnore.size + toAllow.size} attempted, failed or skipped stay retryable")
        }
        persist(updatedIgnoredSet(ignoredPackages(), ignoreSucceeded, restoreSucceeded))
    }

    private fun persist(newSet: Set<String>) {
        synchronized(ignored) {
            ignored.clear()
            ignored.addAll(newSet)
            if (newSet.isEmpty()) {
                prefs.edit().remove(KEY_IGNORED).apply()
            } else {
                prefs.edit().putStringSet(KEY_IGNORED, newSet).apply()
            }
        }
    }

    /** Restores the microphone for every app this controller silenced; unconfirmed ones stay tracked and retryable. */
    fun restoreAll() {
        val all = ignoredPackages()
        if (all.isEmpty()) return
        val results = ShizukuEnforcer.setMicModesVerified(all.map { it to true })
        val restored = all.filter { results[it] == true }.toSet()
        if (restored.size < all.size) {
            Log.w(TAG, "restoreAll: ${all.size - restored.size} package(s) not confirmed; kept for retry")
        }
        persist(updatedIgnoredSet(all, emptySet(), restored))
    }

    companion object {
        private const val TAG = "MicBlockController"
        private const val PREFS_NAME = "mic_block_state"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_IGNORED = "ignored_pkgs"

        /** Pure bookkeeping so it can be unit tested: add confirmed ignores, drop confirmed restores. */
        fun updatedIgnoredSet(
            current: Set<String>,
            ignoreSucceeded: Set<String>,
            restoreSucceeded: Set<String>
        ): Set<String> = (current + ignoreSucceeded) - restoreSucceeded
    }

}
