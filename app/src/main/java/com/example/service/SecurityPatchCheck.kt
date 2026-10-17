package com.example.service

import android.os.Build

/**
 * Security patch check card. Shows the phone's own Android security patch level and compares it
 * with the newest level known when this app version was built.
 *
 * Honest by design:
 * - An app CANNOT install or integrate an OS security patch; only the phone's system update can.
 * - The "latest known" level is a fixed value shipped with the app (source and its date are
 *   shown), not a live lookup and never a guess. If the phone's level is unreadable, the card
 *   says Unavailable.
 * - Nothing is sent anywhere; Build.VERSION.SECURITY_PATCH needs no permission.
 */
object SecurityPatchCheck {

    /** Newest Android security patch level known at the time this app version was built. */
    const val LATEST_KNOWN_PATCH = "2026-09-05"
    const val LATEST_KNOWN_SOURCE = "Google Android Security Bulletin - September 2026"
    const val LATEST_KNOWN_SOURCE_DATE = "checked 17 Oct 2026"

    enum class Status { UP_TO_DATE, BEHIND, UNKNOWN }

    data class Result(
        val status: Status,
        val devicePatch: String?,
        val monthsBehind: Int
    )

    fun check(): Result = evaluate(
        try {
            Build.VERSION.SECURITY_PATCH
        } catch (e: Throwable) {
            null
        }
    )

    /** Pure decision so it can be unit tested. */
    fun evaluate(devicePatch: String?, latestKnown: String = LATEST_KNOWN_PATCH): Result {
        val dev = parse(devicePatch) ?: return Result(Status.UNKNOWN, devicePatch, 0)
        val latest = parse(latestKnown) ?: return Result(Status.UNKNOWN, devicePatch, 0)
        val behind = (latest.first - dev.first) * 12 + (latest.second - dev.second)
        return when {
            behind <= 0 -> Result(Status.UP_TO_DATE, devicePatch, 0)
            else -> Result(Status.BEHIND, devicePatch, behind)
        }
    }

    /** Parses "YYYY-MM-DD" into (year, month); anything unreadable is null. */
    private fun parse(level: String?): Pair<Int, Int>? {
        if (level.isNullOrBlank()) return null
        val parts = level.trim().split("-")
        if (parts.size < 2) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (y < 2015 || m !in 1..12) return null
        return y to m
    }
}
