package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.example.SensorGuardApplication
import com.example.data.model.LedgerCategory
import com.example.data.model.EnforcementAction
import com.example.data.model.LedgerRisk
import com.example.data.model.EventSeverity
import com.example.data.model.TriState

class SensorGuardAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            if (pkgName.isNotEmpty() && pkgName != packageName) {
                // Track foreground app transition for security logging
                val app = applicationContext as? SensorGuardApplication ?: return
                val repository = app.repository
                // Check if background mic access policy is active
                val state = repository.guardState.value
                if (!state.isServiceRunning) return

                val isScreenActive = state.screenState == com.example.data.model.ScreenStateEnum.SCREEN_ON ||
                        state.screenState == com.example.data.model.ScreenStateEnum.SCREEN_UNLOCKED

                if (!isScreenActive && state.settings.micGuardEnabled) {
                    // Screen is OFF and mic guard is armed: verify package is not holding unauthorized background mic
                    Log.d("SensorGuardAccessibility", "Active foreground transition while screen OFF: $pkgName")
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w("SensorGuardAccessibility", "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("SensorGuardAccessibility", "SensorGuard Accessibility & Background Protection Service connected successfully")
    }
}
