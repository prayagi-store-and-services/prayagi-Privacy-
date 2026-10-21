package com.example.data.model

/** An app that currently has an accessibility service ENABLED. */
data class AccessibilityRiskApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

data class AppRiskInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val hasMicrophonePermission: Boolean,
    val hasCameraPermission: Boolean,
    val hasLocationPermission: Boolean,
    val hasPhoneStatePermission: Boolean,
    val hasContactsPermission: Boolean,
    val hasSmsPermission: Boolean,
    val hasOverlayPermission: Boolean,
    val hasEnabledAccessibilityService: Boolean = false,
    /** True only when the app is actually ALLOWED to draw over other apps right now (not just listed in its manifest). */
    val overlayAllowed: Boolean = false,
    val riskScore: Int, // 0 - 100
    val riskLevel: RiskLevel,
    val riskFactors: List<String>
)

enum class RiskLevel {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    MINIMAL
}
