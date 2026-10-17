package com.example

import com.example.service.MicBlockController
import com.example.service.ShizukuEnforcer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuEnforcerTest {

    @Test fun backgroundMicAppIsSilenced() {
        val (ignore, allow) = ShizukuEnforcer.decideMicModes(
            setOf("com.example.spy"), "com.whatsapp", emptySet()
        )
        assertEquals(setOf("com.example.spy"), ignore)
        assertTrue(allow.isEmpty())
    }

    @Test fun foregroundAppIsNeverSilenced() {
        val (ignore, allow) = ShizukuEnforcer.decideMicModes(
            setOf("com.whatsapp"), "com.whatsapp", emptySet()
        )
        assertTrue(ignore.isEmpty())
        assertTrue(allow.isEmpty())
    }

    @Test fun unknownForegroundSilencesNothing() {
        // Fail safe: Usage access off means we do not know what is on screen, so never block.
        val (ignore, allow) = ShizukuEnforcer.decideMicModes(
            setOf("com.example.spy"), null, emptySet()
        )
        assertTrue(ignore.isEmpty())
        assertTrue(allow.isEmpty())
    }

    @Test fun phoneAndSystemAppsAreNeverSilenced() {
        val system = setOf(
            "android", "com.android.systemui", "com.android.phone",
            "com.android.server.telecom", "com.android.incallui",
            "com.google.android.dialer", "com.samsung.android.dialer",
            "com.samsung.android.incallui", "com.google.android.googlequicksearchbox"
        )
        val (ignore, allow) = ShizukuEnforcer.decideMicModes(system, "com.whatsapp", emptySet())
        assertTrue(ignore.isEmpty())
        assertTrue(allow.isEmpty())
    }

    @Test fun alreadySilencedAppIsNotSilencedAgain() {
        val (ignore, allow) = ShizukuEnforcer.decideMicModes(
            setOf("com.example.spy"), "com.whatsapp", setOf("com.example.spy")
        )
        assertTrue(ignore.isEmpty())
        assertTrue(allow.isEmpty())
    }

    @Test fun silencedAppIsRestoredWhenOpened() {
        val (ignore, allow) = ShizukuEnforcer.decideMicModes(
            emptySet(), "com.example.spy", setOf("com.example.spy", "com.example.other")
        )
        assertTrue(ignore.isEmpty())
        assertEquals(setOf("com.example.spy"), allow)
    }

    @Test fun otherSilencedAppsStaySilenced() {
        val (ignore, allow) = ShizukuEnforcer.decideMicModes(
            setOf("com.example.spy"), "com.whatsapp", setOf("com.example.other")
        )
        assertEquals(setOf("com.example.spy"), ignore)
        assertTrue(allow.isEmpty())
    }
}

class MicBlockBookkeepingTest {

    @Test fun confirmedIgnoreEntersSetImmediately() {
        val next = MicBlockController.updatedIgnoredSet(emptySet(), setOf("com.a"), emptySet())
        assertEquals(setOf("com.a"), next)
    }

    @Test fun unconfirmedIgnoreDoesNotEnterSet() {
        // Mid-batch Shizuku loss: second package never attempted, stays out of the tracked set.
        val next = MicBlockController.updatedIgnoredSet(setOf("com.a"), emptySet(), emptySet())
        assertEquals(setOf("com.a"), next)
    }

    @Test fun onlyConfirmedRestoreLeavesSet() {
        val next = MicBlockController.updatedIgnoredSet(
            setOf("com.a", "com.b"), emptySet(), setOf("com.a")
        )
        assertEquals(setOf("com.b"), next)
    }

    @Test fun failedRestoreStaysTrackedAndRetryable() {
        val next = MicBlockController.updatedIgnoredSet(setOf("com.a"), emptySet(), emptySet())
        assertEquals(setOf("com.a"), next)
    }

    @Test fun mixedBatchKeepsExactTruth() {
        val next = MicBlockController.updatedIgnoredSet(
            setOf("com.old"),
            ignoreSucceeded = setOf("com.new1"),
            restoreSucceeded = setOf("com.old")
        )
        assertEquals(setOf("com.new1"), next)
    }
}
