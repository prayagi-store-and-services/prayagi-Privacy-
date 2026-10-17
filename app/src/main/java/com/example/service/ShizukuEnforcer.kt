package com.example.service

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Level 2 background-mic enforcement through Shizuku (free, open-source, no root).
 * Shizuku runs as the shell user, which is allowed to change App Ops. We ask it to run
 * `appops set <pkg> RECORD_AUDIO ignore|allow`. An ignored app keeps its permission but
 * receives silence (zeros) instead of real audio; "allow" restores normal capture.
 *
 * Honest conditions, always shown before enabling:
 * - The free Shizuku app must be installed and running (via Wireless debugging on Android 11+).
 * - Wireless debugging stays on (a small added attack surface).
 * - Shizuku must be started again after every reboot.
 * - System apps (Phone, emergency, assistant) are never blocked; background calls of chat apps
 *   are muted too until that app is opened again (mic returns within seconds).
 * - If Shizuku stops while apps are blocked, those apps keep getting silence until Shizuku runs
 *   again and Level 2 is toggled off/on, or the app's Microphone permission is toggled in system
 *   settings (both reset the block).
 */
object ShizukuEnforcer {

    /** Shizuku service present and answering. */
    fun isServiceRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isServiceRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /**
     * Applies and VERIFIES each package's mode independently: one shell call per package runs
     * `appops set` followed by `appops get`, and the package counts as done only when the read-back
     * shows the requested mode. Returns per-package success; a package missing from the map or
     * mapped to false must be treated as not applied (retryable). Never batch-judge by one exit
     * code: a mid-batch failure would otherwise misrecord which apps are silenced.
     */
    fun setMicModesVerified(commands: List<Pair<String, Boolean>>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        if (commands.isEmpty() || !hasPermission()) return results
        for ((pkg, allow) in commands) {
            val mode = if (allow) "allow" else "ignore"
            results[pkg] = try {
                val process = newShellProcess("appops set $pkg RECORD_AUDIO $mode ; appops get $pkg RECORD_AUDIO") ?: return results
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                process.exitValue() == 0 && output.contains("RECORD_AUDIO: $mode")
            } catch (e: Throwable) {
                return results
            }
            if (!results.getValue(pkg)) return results
        }
        return results
    }

    /**
     * Runs a one-shot shell command as Shizuku's shell user. Shizuku API 13 made newProcess
     * private (deprecated in favor of a full UserService); for single commands the documented
     * fallback is calling it by reflection. ShizukuRemoteProcess extends java.lang.Process,
     * so the cast to the public Process type is safe. Returns null on any failure.
     */
    private fun newShellProcess(script: String): Process? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, arrayOf("sh", "-c", script), null, null) as Process
        } catch (e: Throwable) {
            null
        }
    }

    /** Packages that must never be blocked: calls, emergency and system UI rely on the mic. */
    val NEVER_BLOCK: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.google.android.googlequicksearchbox" // Assistant hotword is a system feature
    )

    /**
     * Pure decision so it can be unit tested. Returns (packages to silence, packages to restore).
     * Fail safe: when the foreground app is unknown (Usage access off), nothing is blocked.
     * A blocked app is restored as soon as it becomes the app open on screen.
     */
    fun decideMicModes(
        micActivePkgs: Set<String>,
        foregroundPkg: String?,
        ignoredPkgs: Set<String>
    ): Pair<Set<String>, Set<String>> {
        val toIgnore = if (foregroundPkg == null) {
            emptySet()
        } else {
            micActivePkgs
                .filter { it != foregroundPkg && it !in NEVER_BLOCK && it !in ignoredPkgs }
                .toSet()
        }
        val toAllow = if (foregroundPkg == null) {
            emptySet()
        } else {
            ignoredPkgs.filter { it == foregroundPkg }.toSet()
        }
        return toIgnore to toAllow
    }
}
