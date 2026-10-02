package com.example.data.model

enum class ProtectionStatus {
    PROTECTED,           // Guard is active and enforcing policy (e.g. screen off, locked)
    MONITORING_IDLE,     // Guard is active in monitoring mode (screen on, sensors accessible)
    EXCEPTION_CALL,      // Temporary exception granted because active or incoming call detected
    PARTIALLY_PROTECTED, // Guard enabled but some permissions or device admin missing
    DISABLED             // Sensor guard service is turned off
}

enum class ScreenStateEnum {
    SCREEN_ON,
    SCREEN_OFF,
    SCREEN_UNLOCKED
}

enum class CallStateEnum {
    IDLE,
    RINGING,
    OFFHOOK // Active Outgoing / In-Progress Call
}

enum class AudioLockMode {
    EXCLUSIVE_LINE_RESERVATION, // Hardware AudioRecord reservation (Maximum Security)
    AUDIO_FOCUS_ONLY           // Transient Audio Focus intercept (Power Saver)
}

data class GuardSettings(
    val autoGuardOnScreenOff: Boolean = true,
    val allowCallsException: Boolean = true,
    val micGuardEnabled: Boolean = true,
    val camGuardEnabled: Boolean = true,
    val screenOffDelaySeconds: Int = 0,
    val soundAlertOnScreenOff: Boolean = false,
    val onboardingCompleted: Boolean = true,
    val audioLockMode: AudioLockMode = AudioLockMode.EXCLUSIVE_LINE_RESERVATION,
    val scheduleEnabled: Boolean = false,
    val scheduleStartHour: Int = 22,
    val scheduleStartMinute: Int = 0,
    val scheduleEndHour: Int = 7,
    val scheduleEndMinute: Int = 0,
    val biometricLockEnabled: Boolean = false,
    val headsetExceptionEnabled: Boolean = true,
    val wifiProfileEnabled: Boolean = false,
    val trustedNetworkName: String = ""
)

data class AppPermissionItem(
    val packageName: String,
    val appName: String,
    val hasMic: Boolean,
    val hasCamera: Boolean,
    val isSystemApp: Boolean
)

data class LiveGuardState(
    val isServiceRunning: Boolean = true,
    val status: ProtectionStatus = ProtectionStatus.MONITORING_IDLE,
    val screenState: ScreenStateEnum = ScreenStateEnum.SCREEN_ON,
    val callState: CallStateEnum = CallStateEnum.IDLE,
    val isMicLocked: Boolean = false,
    val isCamBlocked: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val isEmergencyLockdown: Boolean = false,
    val isHeadsetConnected: Boolean = false,
    val currentNetworkName: String = "Mobile / Unknown",
    val lastEventSummary: String = "Guard active & continuous monitoring enabled",
    val activeEventsCount: Int = 0,
    val settings: GuardSettings = GuardSettings()
)
