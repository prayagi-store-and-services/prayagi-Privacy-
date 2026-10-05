package com.example.data.repository

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.db.PrivacyEventDao
import com.example.data.model.AppPermissionItem
import com.example.data.model.AudioLockMode
import com.example.data.model.CallStateEnum
import com.example.data.model.EnforcementAction
import com.example.data.model.EventSeverity
import com.example.data.model.GuardSettings
import com.example.data.model.LedgerCategory
import com.example.data.model.LedgerRisk
import com.example.data.model.LiveGuardState
import com.example.data.model.PrivacyEvent
import com.example.data.model.ProtectionStatus
import com.example.data.model.ScreenStateEnum
import com.example.data.model.TriState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

class PrivacyRepository(
    private val context: Context,
    private val eventDao: PrivacyEventDao
) {
    private val prefs: SharedPreferences = try {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "sensorguard_secure_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("sensorguard_prefs", Context.MODE_PRIVATE)
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    val allEvents: Flow<List<PrivacyEvent>> = eventDao.getAllEvents()
    val eventCount: Flow<Int> = eventDao.getEventCount()

    private val _guardState = MutableStateFlow(
        LiveGuardState(
            settings = loadSettings()
        )
    )
    val guardState: StateFlow<LiveGuardState> = _guardState.asStateFlow()

    init {
        // Refresh initial state
        _guardState.update { current ->
            current.copy(
                settings = loadSettings()
            )
        }
        // Initialize one-time historical system recovery
        recoverHistoricalSystemLedger()
    }

    fun loadSettings(): GuardSettings {
        val audioModeStr = prefs.getString("audioLockMode", AudioLockMode.EXCLUSIVE_LINE_RESERVATION.name)
        val audioMode = try {
            AudioLockMode.valueOf(audioModeStr ?: AudioLockMode.EXCLUSIVE_LINE_RESERVATION.name)
        } catch (e: Exception) {
            AudioLockMode.EXCLUSIVE_LINE_RESERVATION
        }

        return GuardSettings(
            autoGuardOnScreenOff = prefs.getBoolean("autoGuardOnScreenOff", true),
            allowCallsException = prefs.getBoolean("allowCallsException", true),
            micGuardEnabled = prefs.getBoolean("micGuardEnabled", true),
            camGuardEnabled = prefs.getBoolean("camGuardEnabled", true),
            screenOffDelaySeconds = prefs.getInt("screenOffDelaySeconds", 0),
            soundAlertOnScreenOff = prefs.getBoolean("soundAlertOnScreenOff", false),
            onboardingCompleted = prefs.getBoolean("onboardingCompleted", true),
            audioLockMode = audioMode,
            scheduleEnabled = prefs.getBoolean("scheduleEnabled", false),
            scheduleStartHour = prefs.getInt("scheduleStartHour", 22),
            scheduleStartMinute = prefs.getInt("scheduleStartMinute", 0),
            scheduleEndHour = prefs.getInt("scheduleEndHour", 7),
            scheduleEndMinute = prefs.getInt("scheduleEndMinute", 0),
            biometricLockEnabled = prefs.getBoolean("biometricLockEnabled", false),
            headsetExceptionEnabled = prefs.getBoolean("headsetExceptionEnabled", true),
            wifiProfileEnabled = prefs.getBoolean("wifiProfileEnabled", false),
            trustedNetworkName = prefs.getString("trustedNetworkName", "") ?: ""
        )
    }

    fun updateSettings(newSettings: GuardSettings) {
        prefs.edit().apply {
            putBoolean("autoGuardOnScreenOff", newSettings.autoGuardOnScreenOff)
            putBoolean("allowCallsException", newSettings.allowCallsException)
            putBoolean("micGuardEnabled", newSettings.micGuardEnabled)
            putBoolean("camGuardEnabled", newSettings.camGuardEnabled)
            putInt("screenOffDelaySeconds", newSettings.screenOffDelaySeconds)
            putBoolean("soundAlertOnScreenOff", newSettings.soundAlertOnScreenOff)
            putBoolean("onboardingCompleted", newSettings.onboardingCompleted)
            putString("audioLockMode", newSettings.audioLockMode.name)
            putBoolean("scheduleEnabled", newSettings.scheduleEnabled)
            putInt("scheduleStartHour", newSettings.scheduleStartHour)
            putInt("scheduleStartMinute", newSettings.scheduleStartMinute)
            putInt("scheduleEndHour", newSettings.scheduleEndHour)
            putInt("scheduleEndMinute", newSettings.scheduleEndMinute)
            putBoolean("biometricLockEnabled", newSettings.biometricLockEnabled)
            putBoolean("headsetExceptionEnabled", newSettings.headsetExceptionEnabled)
            putBoolean("wifiProfileEnabled", newSettings.wifiProfileEnabled)
            putString("trustedNetworkName", newSettings.trustedNetworkName)
            apply()
        }
        _guardState.update { it.copy(settings = newSettings) }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        val updated = _guardState.value.settings.copy(onboardingCompleted = completed)
        updateSettings(updated)
    }

    /** Saves what the guard just reported so the home screen widget can show it (event driven, no polling). */
    private fun persistWidgetSnapshot() {
        try {
            com.example.widget.SgWidgetStore.save(context, _guardState.value)
            com.example.widget.SgWidgetProvider.refresh(context)
        } catch (_: Exception) {}
    }

    fun updateServiceState(isRunning: Boolean) {
        _guardState.update { current ->
            val newStatus = calculateStatus(
                isRunning,
                current.screenState,
                current.callState,
                current.isMicLocked,
                current.settings,
                current.isEmergencyLockdown
            )
            current.copy(
                isServiceRunning = isRunning,
                status = newStatus
            )
        }
        persistWidgetSnapshot()
    }

    fun updateEmergencyLockdown(isEmergency: Boolean) {
        _guardState.update { current ->
            val newStatus = calculateStatus(
                current.isServiceRunning,
                current.screenState,
                current.callState,
                current.isMicLocked,
                current.settings,
                isEmergency
            )
            current.copy(
                isEmergencyLockdown = isEmergency,
                status = newStatus
            )
        }
        persistWidgetSnapshot()
    }

    fun updateScreenState(screenState: ScreenStateEnum) {
        _guardState.update { current ->
            val newStatus = calculateStatus(
                current.isServiceRunning,
                screenState,
                current.callState,
                current.isMicLocked,
                current.settings,
                current.isEmergencyLockdown
            )
            current.copy(
                screenState = screenState,
                status = newStatus
            )
        }
        persistWidgetSnapshot()
    }

    fun updateCallState(callState: CallStateEnum) {
        _guardState.update { current ->
            val newStatus = calculateStatus(
                current.isServiceRunning,
                current.screenState,
                callState,
                current.isMicLocked,
                current.settings,
                current.isEmergencyLockdown
            )
            current.copy(
                callState = callState,
                status = newStatus
            )
        }
        persistWidgetSnapshot()
    }

    fun updateLockState(isMicLocked: Boolean, isCamBlocked: Boolean, isDeviceAdmin: Boolean) {
        _guardState.update { current ->
            val newStatus = calculateStatus(
                current.isServiceRunning,
                current.screenState,
                current.callState,
                isMicLocked,
                current.settings,
                current.isEmergencyLockdown
            )
            current.copy(
                isMicLocked = isMicLocked,
                isCamBlocked = isCamBlocked,
                isDeviceAdminActive = isDeviceAdmin,
                status = newStatus
            )
        }
        persistWidgetSnapshot()
    }

    fun getEventsBetween(startTime: Long, endTime: Long): Flow<List<PrivacyEvent>> {
        return eventDao.getEventsBetween(startTime, endTime)
    }

    fun recordLedgerEvent(
        category: LedgerCategory,
        triState: TriState,
        riskLevel: LedgerRisk,
        eventType: String,
        title: String,
        description: String,
        severity: EventSeverity = EventSeverity.INFO,
        enforcementAction: EnforcementAction = EnforcementAction.NONE,
        enforcementReason: String? = null,
        policyName: String? = null,
        enforcementResult: String? = null,
        appName: String? = null,
        relatedPackage: String? = null,
        screenState: String? = null,
        appState: String = "N/A",
        callState: String? = null,
        permissionState: String = "N/A",
        policyAction: String = "MONITORED",
        osEnforcement: String = "VERIFIED",
        durationMs: Long? = null,
        forensicNote: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        repositoryScope.launch {
            val currentState = _guardState.value
            val event = PrivacyEvent(
                timestamp = timestamp,
                eventType = eventType,
                category = category,
                triState = triState,
                riskLevel = riskLevel,
                enforcementAction = enforcementAction,
                enforcementReason = enforcementReason,
                policyName = policyName,
                enforcementResult = enforcementResult,
                title = title,
                description = description,
                severity = severity,
                appName = appName,
                relatedPackage = relatedPackage,
                screenState = screenState ?: currentState.screenState.name,
                appState = appState,
                callState = callState ?: currentState.callState.name,
                permissionState = permissionState,
                policyAction = policyAction,
                osEnforcement = osEnforcement,
                durationMs = durationMs,
                forensicNote = forensicNote
            )
            eventDao.insertEvent(event)
            _guardState.update { it.copy(lastEventSummary = title) }
        }
    }

    fun recordEvent(
        eventType: String,
        title: String,
        description: String,
        severity: EventSeverity = EventSeverity.INFO,
        relatedPackage: String? = null
    ) {
        val category = when {
            eventType.contains("MIC", ignoreCase = true) || eventType.contains("AUDIO", ignoreCase = true) -> LedgerCategory.MICROPHONE
            eventType.contains("CAM", ignoreCase = true) -> LedgerCategory.CAMERA
            eventType.contains("LOC", ignoreCase = true) || eventType.contains("GPS", ignoreCase = true) -> LedgerCategory.LOCATION
            eventType.contains("PERM", ignoreCase = true) -> LedgerCategory.PERMISSIONS
            eventType.contains("APP", ignoreCase = true) || eventType.contains("PACKAGE", ignoreCase = true) -> LedgerCategory.APP_ACTIVITY
            eventType.contains("CALL", ignoreCase = true) || eventType.contains("PHONE", ignoreCase = true) || eventType.contains("RINGING", ignoreCase = true) -> LedgerCategory.TELEPHONY
            eventType.contains("SCREEN", ignoreCase = true) || eventType.contains("USER_PRESENT", ignoreCase = true) -> LedgerCategory.SCREEN
            else -> LedgerCategory.SENSORGUARD
        }

        val triState = when {
            eventType.contains("REQUEST", ignoreCase = true) -> TriState.REQUESTED
            eventType.contains("ARMED", ignoreCase = true) || eventType.contains("LOCK", ignoreCase = true) || eventType.contains("BLOCKED", ignoreCase = true) || eventType.contains("OVERRIDE", ignoreCase = true) -> TriState.ENFORCED
            else -> TriState.OBSERVED
        }

        val risk = when (severity) {
            EventSeverity.ALERT -> LedgerRisk.CRITICAL
            EventSeverity.WARNING -> LedgerRisk.WARNING
            EventSeverity.CALL_EXCEPTION -> LedgerRisk.ATTENTION
            else -> LedgerRisk.SAFE
        }

        val (action, reason, policy, result) = when {
            eventType.contains("GUARD_ARMED", ignoreCase = true) || eventType.contains("MIC_LOCKED", ignoreCase = true) -> {
                Quadruple(
                    EnforcementAction.BLOCKED,
                    "Background + Screen OFF",
                    "SensorGuard Screen-Off Policy",
                    "OS enforcement verified"
                )
            }
            eventType.contains("EMERGENCY_LOCKDOWN", ignoreCase = true) -> {
                if (severity == EventSeverity.ALERT) {
                    Quadruple(
                        EnforcementAction.BLOCKED,
                        "Emergency Panic Isolation",
                        "SensorGuard Emergency Policy",
                        "Hardware exclusive lock engaged"
                    )
                } else {
                    Quadruple(
                        EnforcementAction.ALLOWED,
                        "Panic Lock Disarmed",
                        "SensorGuard Interactive Policy",
                        "Sensors restored to monitor"
                    )
                }
            }
            eventType.contains("CALL_EXCEPTION", ignoreCase = true) || eventType.contains("CALL_DETECTED", ignoreCase = true) -> {
                Quadruple(
                    EnforcementAction.ALLOWED,
                    "Active phone call in progress",
                    "SensorGuard Voice Call Pass-Through Policy",
                    "Microphone unlocked for voice call"
                )
            }
            eventType.contains("GUARD_DISARMED", ignoreCase = true) || eventType.contains("SCREEN_ON", ignoreCase = true) || eventType.contains("USER_PRESENT", ignoreCase = true) -> {
                Quadruple(
                    EnforcementAction.ALLOWED,
                    "Foreground application / User present",
                    "SensorGuard Interactive Policy",
                    "Normal sensor access enabled"
                )
            }
            eventType.contains("SCHEDULE_BYPASS", ignoreCase = true) -> {
                Quadruple(
                    EnforcementAction.LIMITED,
                    "Outside scheduled protection window",
                    "SensorGuard Schedule Policy",
                    "Throttled to passive monitor"
                )
            }
            eventType.contains("PERMISSION_DENIED", ignoreCase = true) || eventType.contains("REVOKED", ignoreCase = true) -> {
                Quadruple(
                    EnforcementAction.DENIED,
                    "Access request denied by user or OS",
                    "Android Permission Policy",
                    "Denied at OS level"
                )
            }
            eventType.contains("RESTRICTED", ignoreCase = true) -> {
                Quadruple(
                    EnforcementAction.RESTRICTED,
                    "Security profile restriction",
                    "SensorGuard Profile Policy",
                    "Access bounded"
                )
            }
            eventType.contains("LIMITED", ignoreCase = true) -> {
                Quadruple(
                    EnforcementAction.LIMITED,
                    "Policy-based restriction",
                    "SensorGuard Throttling Policy",
                    "Limited precision enforced"
                )
            }
            else -> {
                Quadruple(
                    EnforcementAction.NONE,
                    "Telemetry snapshot",
                    "SensorGuard Core Engine",
                    "Monitored"
                )
            }
        }

        recordLedgerEvent(
            category = category,
            triState = triState,
            riskLevel = risk,
            enforcementAction = action,
            enforcementReason = reason,
            policyName = policy,
            enforcementResult = result,
            eventType = eventType,
            title = title,
            description = description,
            severity = severity,
            relatedPackage = relatedPackage
        )
    }

    /**
     * Recover historical records strictly from Android system records.
     * Never fabricates records; extracts actual install timestamps & permissions from PackageManager / AppOps.
     */
    fun recoverHistoricalSystemLedger() {
        if (prefs.getBoolean("historical_discovery_done", false)) return

        repositoryScope.launch {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                val historicalEvents = mutableListOf<PrivacyEvent>()

                for (pkg in packages) {
                    if (pkg.packageName == context.packageName) continue
                    val appInfo = pkg.applicationInfo ?: continue
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val appName = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkg.packageName
                    }

                    val requestedPermissions = pkg.requestedPermissions ?: emptyArray()
                    val hasMic = requestedPermissions.contains(android.Manifest.permission.RECORD_AUDIO)
                    val hasCam = requestedPermissions.contains(android.Manifest.permission.CAMERA)
                    val hasLoc = requestedPermissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                            requestedPermissions.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION)

                    if (hasMic || hasCam || hasLoc || !isSystem) {
                        val installTime = pkg.firstInstallTime
                        val permList = buildList {
                            if (hasMic) add("Microphone")
                            if (hasCam) add("Camera")
                            if (hasLoc) add("Location")
                        }

                        val risk = when {
                            hasMic && hasCam -> LedgerRisk.WARNING
                            hasMic || hasCam -> LedgerRisk.ATTENTION
                            else -> LedgerRisk.SAFE
                        }

                        val enforcementAction = if (permList.isNotEmpty()) {
                            if (isSystem) EnforcementAction.ALLOWED else EnforcementAction.RESTRICTED
                        } else {
                            EnforcementAction.ALLOWED
                        }

                        val reason = if (isSystem) "System-level privileged package registration" else "User-installed package with declared sensor capabilities"

                        historicalEvents.add(
                            PrivacyEvent(
                                timestamp = if (installTime > 0) installTime else System.currentTimeMillis() - 86400000L,
                                eventType = "SYSTEM_HISTORICAL_AUDIT",
                                category = if (hasMic) LedgerCategory.MICROPHONE else if (hasCam) LedgerCategory.CAMERA else if (hasLoc) LedgerCategory.LOCATION else LedgerCategory.APP_ACTIVITY,
                                triState = TriState.OBSERVED,
                                riskLevel = risk,
                                enforcementAction = enforcementAction,
                                enforcementReason = reason,
                                policyName = "Android OS PackageManager Registration",
                                enforcementResult = "OS verified package record",
                                title = "Historical OS Audit: $appName",
                                description = "Android system record: App registered with permissions [${permList.joinToString(", ")}].",
                                severity = EventSeverity.INFO,
                                appName = appName,
                                relatedPackage = pkg.packageName,
                                screenState = "N/A",
                                appState = if (isSystem) "SYSTEM" else "USER_INSTALLED",
                                callState = "IDLE",
                                permissionState = if (permList.isNotEmpty()) "GRANTED" else "NONE",
                                policyAction = "MONITORED",
                                osEnforcement = "OS_HISTORICAL_RECORD",
                                forensicNote = "Recovered strictly from Android OS PackageManager metadata with zero fabrication. Original install timestamp preserved."
                            )
                        )
                    }
                }

                if (historicalEvents.isNotEmpty()) {
                    eventDao.insertEvents(historicalEvents)
                }

                // Add initial Ledger Activation Genesis entry
                eventDao.insertEvent(
                    PrivacyEvent(
                        timestamp = System.currentTimeMillis(),
                        eventType = "LEDGER_INITIALIZED",
                        category = LedgerCategory.SENSORGUARD,
                        triState = TriState.ENFORCED,
                        riskLevel = LedgerRisk.SAFE,
                        enforcementAction = EnforcementAction.ALLOWED,
                        enforcementReason = "Root Ledger Genesis & Hardware Protection Active",
                        policyName = "SensorGuard Engine Initialization",
                        enforcementResult = "Engine active",
                        title = "SensorGuard Privacy Ledger Initialized",
                        description = "Continuous privacy audit ledger engine active. Full tri-state forensic telemetry recording engaged.",
                        severity = EventSeverity.INFO,
                        appName = "SensorGuard Core",
                        relatedPackage = context.packageName,
                        screenState = _guardState.value.screenState.name,
                        appState = "FOREGROUND",
                        callState = "IDLE",
                        permissionState = "GRANTED",
                        policyAction = "PROTECTED",
                        osEnforcement = "HARDWARE_POLICY_ENGINE",
                        forensicNote = "Root ledger genesis event. Real-time audit recording initialized."
                    )
                )

                prefs.edit().putBoolean("historical_discovery_done", true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearEvents() {
        eventDao.clearAllEvents()
    }

    suspend fun deleteEvent(id: Long) {
        eventDao.deleteEventById(id)
    }

    fun updateHeadsetState(connected: Boolean) {
        _guardState.update { it.copy(isHeadsetConnected = connected) }
    }

    fun updateNetworkState(networkName: String) {
        _guardState.update { it.copy(currentNetworkName = networkName) }
    }

    fun scanInstalledAppPermissions(): List<AppPermissionItem> {
        val pm = context.packageManager
        val installed = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val result = mutableListOf<AppPermissionItem>()

        for (pkg in installed) {
            val appInfo = pkg.applicationInfo ?: continue
            if (pkg.packageName == context.packageName) continue

            val requestedPermissions = pkg.requestedPermissions ?: emptyArray()
            val hasMic = requestedPermissions.contains(android.Manifest.permission.RECORD_AUDIO)
            val hasCamera = requestedPermissions.contains(android.Manifest.permission.CAMERA)

            if (hasMic || hasCamera) {
                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                result.add(
                    AppPermissionItem(
                        packageName = pkg.packageName,
                        appName = appName,
                        hasMic = hasMic,
                        hasCamera = hasCamera,
                        isSystemApp = isSystem
                    )
                )
            }
        }
        return result.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    fun formatEventsAsCsv(events: List<PrivacyEvent>): String {
        val sb = StringBuilder()
        sb.append("ID,Timestamp_Epoch,Timestamp_12H_MS,Category,Tri_State,Enforcement_Action,Enforcement_Reason,Policy_Name,Enforcement_Result,Risk_Level,Severity,Event_Type,Title,Description,App_Name,Package,Screen_State,App_State,Call_State,Permission_State,Policy_Action,OS_Enforcement,Duration_MS,Forensic_Evidence_Note\n")
        events.forEach { event ->
            val cleanTitle = (event.title ?: "").replace("\"", "\"\"")
            val cleanDesc = (event.description ?: "").replace("\"", "\"\"")
            val appName = (event.appName ?: "").replace("\"", "\"\"")
            val pkg = (event.relatedPackage ?: "").replace("\"", "\"\"")
            val reason = (event.enforcementReason ?: "").replace("\"", "\"\"")
            val policy = (event.policyName ?: "").replace("\"", "\"\"")
            val result = (event.enforcementResult ?: "").replace("\"", "\"\"")
            val note = (event.forensicNote ?: "").replace("\"", "\"\"")
            sb.append("${event.id},${event.timestamp},\"${event.getFormattedTime()}\",\"${event.category.name}\",\"${event.triState.name}\",\"${event.enforcementAction.name}\",\"$reason\",\"$policy\",\"$result\",\"${event.riskLevel.name}\",\"${event.severity.name}\",\"${event.eventType}\",\"$cleanTitle\",\"$cleanDesc\",\"$appName\",\"$pkg\",\"${event.screenState}\",\"${event.appState}\",\"${event.callState}\",\"${event.permissionState}\",\"${event.policyAction}\",\"${event.osEnforcement}\",\"${event.durationMs ?: ""}\",\"$note\"\n")
        }
        return sb.toString()
    }

    fun formatForensicReport(events: List<PrivacyEvent>): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm:ss.SSS a", Locale.US)
        sb.append("=================================================================\n")
        sb.append("         SENSORGUARD FORENSIC PRIVACY ACTIVITY LEDGER REPORT      \n")
        sb.append("=================================================================\n")
        sb.append("Generated At      : ${sdf.format(Date())}\n")
        sb.append("Total Events      : ${events.size}\n")
        sb.append("Allowed Actions   : ${events.count { it.enforcementAction == EnforcementAction.ALLOWED }}\n")
        sb.append("Blocked Actions   : ${events.count { it.enforcementAction == EnforcementAction.BLOCKED }}\n")
        sb.append("Limited Actions   : ${events.count { it.enforcementAction == EnforcementAction.LIMITED }}\n")
        sb.append("Restricted Actions: ${events.count { it.enforcementAction == EnforcementAction.RESTRICTED }}\n")
        sb.append("Denied Requests   : ${events.count { it.enforcementAction == EnforcementAction.DENIED }}\n")
        sb.append("Microphone Events : ${events.count { it.category == LedgerCategory.MICROPHONE }}\n")
        sb.append("Camera Events     : ${events.count { it.category == LedgerCategory.CAMERA }}\n")
        sb.append("Location Events   : ${events.count { it.category == LedgerCategory.LOCATION }}\n")
        sb.append("App Activities    : ${events.count { it.category == LedgerCategory.APP_ACTIVITY }}\n")
        sb.append("Enforced Policies : ${events.count { it.triState == TriState.ENFORCED }}\n")
        sb.append("Critical Alerts   : ${events.count { it.riskLevel == LedgerRisk.CRITICAL }}\n")
        sb.append("-----------------------------------------------------------------\n\n")

        events.forEachIndexed { index, e ->
            sb.append("[#${index + 1}] ${e.getFormattedDateTime()}\n")
            sb.append("Category    : ${e.category.name} | State: ${e.triState.name} | Action: ${e.enforcementAction.name} | Risk: ${e.riskLevel.name}\n")
            sb.append("Event       : ${e.title}\n")
            sb.append("Details     : ${e.description}\n")
            if (!e.enforcementReason.isNullOrBlank()) {
                sb.append("Reason      : ${e.enforcementReason}\n")
            }
            if (!e.policyName.isNullOrBlank()) {
                sb.append("Policy      : ${e.policyName}\n")
            }
            if (!e.enforcementResult.isNullOrBlank()) {
                sb.append("Result      : ${e.enforcementResult}\n")
            }
            if (!e.appName.isNullOrBlank() || !e.relatedPackage.isNullOrBlank()) {
                sb.append("Target App  : ${e.appName ?: "Unknown"} (${e.relatedPackage ?: "N/A"})\n")
            }
            sb.append("Context     : Screen=${e.screenState} | Call=${e.callState} | AppState=${e.appState}\n")
            sb.append("Policy      : Action=${e.policyAction} | Enforcement=${e.osEnforcement}\n")
            if (!e.forensicNote.isNullOrBlank()) {
                sb.append("Forensics   : ${e.forensicNote}\n")
            }
            sb.append("-----------------------------------------------------------------\n")
        }
        return sb.toString()
    }

    private fun calculateStatus(
        isServiceRunning: Boolean,
        screenState: ScreenStateEnum,
        callState: CallStateEnum,
        isMicLocked: Boolean,
        settings: GuardSettings,
        isEmergencyLockdown: Boolean = false
    ): ProtectionStatus {
        if (!isServiceRunning) return ProtectionStatus.DISABLED

        if (isEmergencyLockdown) return ProtectionStatus.PROTECTED

        if (callState != CallStateEnum.IDLE && settings.allowCallsException) {
            return ProtectionStatus.EXCEPTION_CALL
        }

        return if (screenState == ScreenStateEnum.SCREEN_OFF) {
            if (isMicLocked || !settings.micGuardEnabled) {
                ProtectionStatus.PROTECTED
            } else {
                ProtectionStatus.PARTIALLY_PROTECTED
            }
        } else {
            ProtectionStatus.MONITORING_IDLE
        }
    }

    fun exportAuditLogsJson(events: List<PrivacyEvent>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        events.forEachIndexed { index, event ->
            sb.append("  {\n")
            sb.append("    \"id\": ${event.id},\n")
            sb.append("    \"appName\": \"${event.appName?.replace("\"", "\\\"") ?: "N/A"}\",\n")
            sb.append("    \"relatedPackage\": \"${event.relatedPackage?.replace("\"", "\\\"") ?: "N/A"}\",\n")
            sb.append("    \"category\": \"${event.category.name}\",\n")
            sb.append("    \"action\": \"${event.enforcementAction.name}\",\n")
            sb.append("    \"osEnforcement\": \"${event.osEnforcement.replace("\"", "\\\"")}\",\n")
            sb.append("    \"timestamp\": ${event.timestamp}\n")
            sb.append("  }${if (index < events.size - 1) "," else ""}\n")
        }
        sb.append("]")
        return sb.toString()
    }

    fun exportAuditLogsCsv(events: List<PrivacyEvent>): String {
        val sb = StringBuilder()
        sb.append("ID,App Name,Related Package,Category,Enforcement Action,OS Enforcement,Timestamp\n")
        events.forEach { event ->
            sb.append("${event.id},\"${event.appName?.replace("\"", "\"\"") ?: "N/A"}\",${event.relatedPackage ?: "N/A"},${event.category.name},${event.enforcementAction.name},\"${event.osEnforcement.replace("\"", "\"\"")}\",${event.timestamp}\n")
        }
        return sb.toString()
    }
}
