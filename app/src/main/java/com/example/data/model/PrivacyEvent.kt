package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EnforcementAction {
    ALLOWED,     // 🟢 Allowed Access — Foreground app execution, active phone call exception, user allowed
    BLOCKED,     // 🔴 Blocked — Screen-off exclusive audio lock, camera policy disable, lockdown
    LIMITED,     // 🟡 Limited — Throttled background polling, coarse sensor restriction, policy limit
    RESTRICTED,  // 🔵 Restricted — Security profile restriction, audio focus temporary hold
    DENIED,      // ⚠️ Denied — Explicitly denied permission or rejected hardware request
    NONE         // Baseline / informational event
}

enum class LedgerCategory {
    MICROPHONE,
    CAMERA,
    LOCATION,
    PERMISSIONS,
    APP_ACTIVITY,
    SENSORGUARD,
    TELEPHONY,
    SCREEN
}

enum class TriState {
    REQUESTED, // App requested permission or sensor access query
    OBSERVED,  // Android OS exposed actual sensor access or state transition
    ENFORCED   // SensorGuard or DevicePolicyManager applied policy/hardware lock
}

enum class LedgerRisk {
    SAFE,       // Normal user-authorized foreground access
    ATTENTION,  // Legitimate/explainable background or configuration event
    WARNING,    // Potential policy conflict or background access
    CRITICAL    // Unauthorized access attempt or lockdown bypass
}

enum class EventSeverity {
    INFO,
    SHIELD_ON,
    SHIELD_OFF,
    CALL_EXCEPTION,
    WARNING,
    ALERT
}

@Entity(tableName = "privacy_events")
data class PrivacyEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val category: LedgerCategory = LedgerCategory.SENSORGUARD,
    val triState: TriState = TriState.OBSERVED,
    val riskLevel: LedgerRisk = LedgerRisk.SAFE,
    val enforcementAction: EnforcementAction = EnforcementAction.NONE,
    val enforcementReason: String? = null,
    val policyName: String? = null,
    val enforcementResult: String? = null,
    val title: String,
    val description: String,
    val severity: EventSeverity = EventSeverity.INFO,
    val appName: String? = null,
    val relatedPackage: String? = null,
    val screenState: String = "SCREEN_ON",
    val appState: String = "N/A",
    val callState: String = "IDLE",
    val permissionState: String = "N/A",
    val policyAction: String = "MONITORED",
    val osEnforcement: String = "VERIFIED",
    val durationMs: Long? = null,
    val forensicNote: String? = null
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("hh:mm:ss.SSS a", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm:ss.SSS a", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return sdf.format(Date(timestamp))
    }
}
