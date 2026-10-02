package com.example.data.model

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
