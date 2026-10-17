package com.example

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
