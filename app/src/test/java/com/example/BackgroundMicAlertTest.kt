package com.example

import com.example.service.BackgroundMicAlerter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMicAlertTest {
    @Test fun appOnScreenNeverAlerts() {
        assertFalse(BackgroundMicAlerter.shouldAlert("com.whatsapp", "com.whatsapp", 0L, 1_000_000L))
    }
    @Test fun backgroundAppAlerts() {
        assertTrue(BackgroundMicAlerter.shouldAlert("com.example.spy", "com.whatsapp", 0L, 1_000_000L))
    }
    @Test fun phoneAndSystemNeverAlert() {
        assertFalse(BackgroundMicAlerter.shouldAlert("com.android.phone", "com.whatsapp", 0L, 1_000_000L))
        assertFalse(BackgroundMicAlerter.shouldAlert("com.android.server.telecom", "com.whatsapp", 0L, 1_000_000L))
        assertFalse(BackgroundMicAlerter.shouldAlert("android", "com.whatsapp", 0L, 1_000_000L))
    }
    @Test fun cooldownSuppressesRepeat() {
        assertFalse(BackgroundMicAlerter.shouldAlert("com.example.spy", "com.whatsapp", 950_000L, 1_000_000L))
        assertTrue(BackgroundMicAlerter.shouldAlert("com.example.spy", "com.whatsapp", 0L, BackgroundMicAlerter.COOLDOWN_MS + 1))
    }
    @Test fun unknownForegroundNeverAlerts() {
        assertFalse(BackgroundMicAlerter.shouldAlert("com.example.spy", null, 0L, 1_000_000L))
    }
}
