package com.example

import com.example.siteblock.AutoListUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoListUpdateTest {
    private val fmt: (Long) -> String = { "T" + it }

    @Test
    fun neverRunIsHonestAndStatesBothSchedules() {
        val t = AutoListUpdate.statusText(24, 0L, 0L, null, fmt)
        assertTrue(t.contains("Wi-Fi"))
        assertTrue(t.contains("every 24 hours"))
        assertTrue(t.contains("Has not run yet"))
        assertTrue(t.contains("built-in list"))
    }

    @Test
    fun noTextOffersAnOffSwitch() {
        val t = AutoListUpdate.statusText(24, 0L, 0L, null, fmt)
        assertFalse(t.contains("is off"))
        assertFalse(t.contains("turn it off"))
    }

    @Test
    fun failureShowsTheReason() {
        val t = AutoListUpdate.statusText(12, 0L, 5L, "Could not reach GitHub. The built-in list is still in use.", fmt)
        assertTrue(t.contains("did not work"))
        assertTrue(t.contains("Could not reach GitHub"))
        assertTrue(t.contains("every 12 hours"))
    }

    @Test
    fun successShowsListDate() {
        val t = AutoListUpdate.statusText(6, 9L, 9L, null, fmt)
        assertTrue(t.contains("It worked."))
        assertTrue(t.contains("T9"))
    }

    @Test
    fun mobileHoursOnlyAcceptsTheListedChoices() {
        for (h in AutoListUpdate.MOBILE_CHOICES) assertEquals(h, AutoListUpdate.cleanMobileHours(h))
        assertEquals(AutoListUpdate.DEFAULT_MOBILE_HOURS, AutoListUpdate.cleanMobileHours(0))
        assertEquals(AutoListUpdate.DEFAULT_MOBILE_HOURS, AutoListUpdate.cleanMobileHours(7))
        assertEquals(AutoListUpdate.DEFAULT_MOBILE_HOURS, AutoListUpdate.cleanMobileHours(-5))
    }
}
