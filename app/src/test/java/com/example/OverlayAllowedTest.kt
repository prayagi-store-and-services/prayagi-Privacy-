package com.example

import android.app.AppOpsManager
import com.example.service.AppRiskScanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAllowedTest {
    @Test fun allowedModeIsOn() = assertTrue(AppRiskScanner.overlayAllowedFrom(AppOpsManager.MODE_ALLOWED, false))
    @Test fun ignoredAndErroredAreOff() {
        assertFalse(AppRiskScanner.overlayAllowedFrom(AppOpsManager.MODE_IGNORED, true))
        assertFalse(AppRiskScanner.overlayAllowedFrom(AppOpsManager.MODE_ERRORED, true))
    }
    @Test fun defaultFollowsPermission() {
        assertTrue(AppRiskScanner.overlayAllowedFrom(AppOpsManager.MODE_DEFAULT, true))
        assertFalse(AppRiskScanner.overlayAllowedFrom(AppOpsManager.MODE_DEFAULT, false))
    }
}
