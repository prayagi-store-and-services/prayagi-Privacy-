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
    const val LATEST_KNOWN_PATCH = "2026-10-01"
    const val LATEST_KNOWN_SOURCE = "Google Android Security Bulletin - October 2026"
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
        val behind = ((latest.year - dev.year) * 12 + (latest.monthValue - dev.monthValue)).coerceAtLeast(0)
        return when {
            dev.ordinal >= latest.ordinal -> Result(Status.UP_TO_DATE, devicePatch, 0)
            else -> Result(Status.BEHIND, devicePatch, behind)
        }
    }

    private data class PatchDate(val year: Int, val monthValue: Int, val day: Int) {
        val ordinal: Int get() = year * 10000 + monthValue * 100 + day
    }

    /** Requires a real ISO date. Pure arithmetic also works on Android 7 (minSdk 24). */
    private fun parse(level: String?): PatchDate? {
        if (level == null || !Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(level)) return null
        val year = level.substring(0, 4).toInt()
        val month = level.substring(5, 7).toInt()
        val day = level.substring(8, 10).toInt()
        if (year < 2015 || month !in 1..12) return null
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = when (month) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return if (day in 1..days) PatchDate(year, month, day) else null
    }
}
