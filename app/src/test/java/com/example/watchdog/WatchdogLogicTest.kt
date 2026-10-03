package com.example.watchdog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogLogicTest {
    @Test fun unknownAppIsUnavailable() {
        assertEquals("Unavailable", WatchdogLogic.appText(null))
        assertEquals("Unavailable", WatchdogLogic.appText("  "))
        assertEquals("Maps", WatchdogLogic.appText("Maps"))
    }
    @Test fun suspiciousOnlyScreenOffAndNoCall() {
        assertTrue(WatchdogLogic.isSuspicious(screenOn = false, onCall = false))
        assertFalse(WatchdogLogic.isSuspicious(screenOn = true, onCall = false))
        assertFalse(WatchdogLogic.isSuspicious(screenOn = false, onCall = true))
    }
    @Test fun bodyNeverClaimsProof() {
        val b = WatchdogLogic.body("Camera", null)
        assertTrue(b.contains("Which app: Unavailable"))
        assertTrue(b.contains("not proof"))
    }
    @Test fun throttlePerKeyPerMinute() {
        val t = WatchdogLogic.Throttle()
        assertTrue(t.allow("cam", 1000L))
        assertFalse(t.allow("cam", 30_000L))
        assertTrue(t.allow("mic", 30_000L))
        assertTrue(t.allow("cam", 61_001L))
    }
}
