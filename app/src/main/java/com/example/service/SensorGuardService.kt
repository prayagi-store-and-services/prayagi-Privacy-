package com.example.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SensorGuardApplication
import com.example.data.model.CallStateEnum
import com.example.data.model.EnforcementAction
import com.example.data.model.EventSeverity
import com.example.data.model.GuardSettings
import com.example.data.model.LedgerCategory
import com.example.data.model.LedgerRisk
import com.example.data.model.ProtectionStatus
import com.example.data.model.ScreenStateEnum
import com.example.data.model.TriState
import com.example.data.repository.PrivacyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SensorGuardService : Service() {

    private lateinit var repository: PrivacyRepository
    private lateinit var audioLockManager: AudioLockManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var currentScreenState = ScreenStateEnum.SCREEN_ON
    private var currentCallState = CallStateEnum.IDLE
    private var telephonyManager: TelephonyManager? = null

    // For Android 12+ (API 31)
    private var telephonyCallback: TelephonyCallback? = null

    // For pre-Android 12
    @Suppress("DEPRECATION")
    private var legacyPhoneStateListener: PhoneStateListener? = null

    private var screenOffJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var appOpsManager: AppOpsManager? = null
    private var audioManager: AudioManager? = null
    private var audioRecordingCallback: AudioManager.AudioRecordingCallback? = null
    private var opActiveChangedListener: AppOpsManager.OnOpActiveChangedListener? = null
    private var activityManager: ActivityManager? = null

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    val isPlugged = state == 1
                    repository.updateHeadsetState(isPlugged)
                    repository.recordEvent(
                        eventType = "HEADSET_EVENT",
                        title = if (isPlugged) "Headset / Earphones Connected" else "Headset Disconnected",
                        description = if (isPlugged) "Wired audio route active. Adaptive microphone policies ready." else "Wired audio route disconnected.",
                        severity = EventSeverity.INFO
                    )
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
                    val isBtConnected = state == BluetoothHeadset.STATE_CONNECTED
                    repository.updateHeadsetState(isBtConnected)
                    if (isBtConnected) {
                        repository.recordEvent(
                            eventType = "HEADSET_EVENT",
                            title = "Bluetooth Audio Device Connected",
                            description = "Bluetooth headset active.",
                            severity = EventSeverity.INFO
                        )
                    }
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handleScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    handleScreenOn()
                }
                Intent.ACTION_USER_PRESENT -> {
                    handleUserPresent()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as SensorGuardApplication
        repository = app.repository
        audioLockManager = AudioLockManager(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        registerScreenReceiver()
        registerHeadsetReceiver()
        registerNetworkMonitoring()
        registerTelephonyListener()
        registerSensorActivityMonitoring()

        repository.updateServiceState(true)
        repository.recordEvent(
            eventType = "SERVICE_STARTED",
            title = "SensorGuard Engine Active",
            description = "Sensor monitoring and privacy policy engine initialized in background",
            severity = EventSeverity.INFO
        )
    }

    private fun registerHeadsetReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(headsetReceiver, filter)
    }

    private fun registerNetworkMonitoring() {
        val cm = connectivityManager ?: return
        val builder = NetworkRequest.Builder()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                val netName = when {
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi (Connected)"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular Mobile"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                    else -> "Connected Network"
                }
                repository.updateNetworkState(netName)
            }

            override fun onLost(network: Network) {
                repository.updateNetworkState("No Connection / Disconnected")
            }
        }
        networkCallback = callback
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            try {
                cm.registerNetworkCallback(builder.build(), callback)
            } catch (ignored: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_GUARD -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_MANUAL_SHIELD_TOGGLE -> {
                toggleManualShield()
            }
            ACTION_EMERGENCY_LOCKDOWN_ON -> {
                applyEmergencyLockdown(true)
            }
            ACTION_EMERGENCY_LOCKDOWN_OFF -> {
                applyEmergencyLockdown(false)
            }
            else -> {
                startForegroundWithNotification()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildForegroundNotification(
            title = "SensorGuard Active",
            contentText = "Continuous privacy & sensor shield active"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun registerTelephonyListener() {
        try {
            val tm = telephonyManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = @RequiresApi(Build.VERSION_CODES.S) object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateTransition(state)
                    }
                }
                telephonyCallback = callback
                tm.registerTelephonyCallback(mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateTransition(state)
                    }
                }
                legacyPhoneStateListener = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted for telephony callback: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering telephony listener: ${e.message}")
        }
    }

    private fun handleScreenOff() {
        currentScreenState = ScreenStateEnum.SCREEN_OFF
        repository.updateScreenState(ScreenStateEnum.SCREEN_OFF)

        val settings = repository.loadSettings()
        if (!settings.autoGuardOnScreenOff) {
            repository.recordEvent(
                eventType = "SCREEN_OFF",
                title = "Screen Off (Auto-Guard Disabled)",
                description = "Screen turned off. Auto-guard is disabled in settings.",
                severity = EventSeverity.INFO
            )
            updateNotification("Screen Off (Monitoring)", "Auto-shield is disabled in settings")
            return
        }

        // Cancel any pending delay job
        screenOffJob?.cancel()

        if (settings.screenOffDelaySeconds > 0) {
            screenOffJob = serviceScope.launch {
                delay(settings.screenOffDelaySeconds * 1000L)
                applyScreenOffProtection()
            }
        } else {
            applyScreenOffProtection()
        }
    }

    private fun applyScreenOffProtection() {
        val settings = repository.loadSettings()
        
        // Schedule Check
        if (settings.scheduleEnabled && !isCurrentTimeInSchedule(settings)) {
            repository.recordEvent(
                eventType = "SCHEDULE_BYPASS",
                title = "Schedule Window Inactive",
                description = "Screen off, but outside configured schedule protection hours.",
                severity = EventSeverity.INFO
            )
            updateNotification("SCHEDULE INACTIVE", "Outside configured protection window")
            return
        }

        if (currentCallState != CallStateEnum.IDLE && settings.allowCallsException) {
            // Voice call is in progress! Grant exception
            repository.recordEvent(
                eventType = "CALL_EXCEPTION",
                title = "Screen Off: Call Exception Active",
                description = "Screen is off, but microphone remains unlocked for active voice call.",
                severity = EventSeverity.CALL_EXCEPTION
            )
            updateNotification("VOICE CALL ACTIVE", "Microphone unlocked for active phone call")
            return
        }

        var micLocked = false
        var camBlocked = false

        if (settings.micGuardEnabled) {
            micLocked = audioLockManager.acquireMicrophoneLock(settings.audioLockMode)
        }

        if (settings.camGuardEnabled && SensorGuardAdminReceiver.isAdminActive(this)) {
            camBlocked = SensorGuardAdminReceiver.setCameraDisabled(this, true)
        }

        val isAdmin = SensorGuardAdminReceiver.isAdminActive(this)
        repository.updateLockState(isMicLocked = micLocked, isCamBlocked = camBlocked, isDeviceAdmin = isAdmin)

        repository.recordEvent(
            eventType = "GUARD_ARMED",
            title = "SHIELD ARMED: Screen Off",
            description = "Microphone exclusive guard lock engaged (${settings.audioLockMode.name}). Unauthorized background taps blocked.",
            severity = EventSeverity.SHIELD_ON
        )

        updateNotification(
            "SHIELD ACTIVE (Screen Off)",
            if (micLocked) "Microphone is locked against background capture" else "Sensor guard active"
        )
    }

    private fun isCurrentTimeInSchedule(settings: GuardSettings): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        val startMinutes = settings.scheduleStartHour * 60 + settings.scheduleStartMinute
        val endMinutes = settings.scheduleEndHour * 60 + settings.scheduleEndMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Overnights (e.g. 22:00 to 07:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    private fun applyEmergencyLockdown(enable: Boolean) {
        val settings = repository.loadSettings()
        if (enable) {
            val micLocked = audioLockManager.acquireMicrophoneLock(settings.audioLockMode)
            var camBlocked = false
            if (SensorGuardAdminReceiver.isAdminActive(this)) {
                camBlocked = SensorGuardAdminReceiver.setCameraDisabled(this, true)
            }
            repository.updateEmergencyLockdown(true)
            repository.updateLockState(isMicLocked = micLocked, isCamBlocked = camBlocked, isDeviceAdmin = SensorGuardAdminReceiver.isAdminActive(this))
            repository.recordEvent(
                eventType = "EMERGENCY_LOCKDOWN",
                title = "EMERGENCY PRIVACY LOCKDOWN ENGAGED",
                description = "Immediate hardware-level sensor isolation activated by user.",
                severity = EventSeverity.ALERT
            )
            updateNotification("🚨 EMERGENCY LOCKDOWN ACTIVE", "All hardware sensors restricted immediately")
        } else {
            audioLockManager.releaseMicrophoneLock()
            if (SensorGuardAdminReceiver.isAdminActive(this)) {
                SensorGuardAdminReceiver.setCameraDisabled(this, false)
            }
            repository.updateEmergencyLockdown(false)
            repository.updateLockState(isMicLocked = false, isCamBlocked = false, isDeviceAdmin = SensorGuardAdminReceiver.isAdminActive(this))
            repository.recordEvent(
                eventType = "EMERGENCY_LOCKDOWN",
                title = "Emergency Lockdown Disarmed",
                description = "Emergency hardware isolation disarmed. Returned to standard monitor mode.",
                severity = EventSeverity.INFO
            )
            updateNotification("SENSOR MONITOR: Idle", "Screen active - Sensors normally accessible")
        }
    }

    private fun handleScreenOn() {
        currentScreenState = ScreenStateEnum.SCREEN_ON
        screenOffJob?.cancel()
        repository.updateScreenState(ScreenStateEnum.SCREEN_ON)

        // Release sensor guard lock when screen turns on
        releaseProtectionForScreenOn("Screen On: Returned to Idle Monitor Mode")
    }

    private fun handleUserPresent() {
        currentScreenState = ScreenStateEnum.SCREEN_UNLOCKED
        screenOffJob?.cancel()
        repository.updateScreenState(ScreenStateEnum.SCREEN_UNLOCKED)
        releaseProtectionForScreenOn("Device Unlocked: User Active")
    }

    private fun releaseProtectionForScreenOn(reason: String) {
        val wasLocked = audioLockManager.isLocked
        audioLockManager.releaseMicrophoneLock()

        val settings = repository.loadSettings()
        if (settings.camGuardEnabled && SensorGuardAdminReceiver.isAdminActive(this)) {
            SensorGuardAdminReceiver.setCameraDisabled(this, false)
        }

        val isAdmin = SensorGuardAdminReceiver.isAdminActive(this)
        repository.updateLockState(isMicLocked = false, isCamBlocked = false, isDeviceAdmin = isAdmin)

        if (wasLocked) {
            repository.recordEvent(
                eventType = "GUARD_DISARMED",
                title = "Shield Disarmed",
                description = reason,
                severity = EventSeverity.SHIELD_OFF
            )
        }

        updateNotification("SENSOR MONITOR: Idle", "Screen active - Sensors normally accessible")
    }

    private fun handleCallStateTransition(state: Int) {
        val newState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CallStateEnum.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallStateEnum.OFFHOOK
            else -> CallStateEnum.IDLE
        }

        if (newState == currentCallState) return
        currentCallState = newState
        repository.updateCallState(newState)

        val settings = repository.loadSettings()

        if (newState == CallStateEnum.RINGING || newState == CallStateEnum.OFFHOOK) {
            // Incoming or active call! Grant exception immediately
            if (settings.allowCallsException) {
                audioLockManager.releaseMicrophoneLock()
                if (settings.camGuardEnabled && SensorGuardAdminReceiver.isAdminActive(this)) {
                    SensorGuardAdminReceiver.setCameraDisabled(this, false)
                }

                val isAdmin = SensorGuardAdminReceiver.isAdminActive(this)
                repository.updateLockState(isMicLocked = false, isCamBlocked = false, isDeviceAdmin = isAdmin)

                repository.recordEvent(
                    eventType = "CALL_DETECTED",
                    title = if (newState == CallStateEnum.RINGING) "Incoming Call Detected" else "Call Active (Off-hook)",
                    description = "Temporary exception granted: Microphone released for phone call.",
                    severity = EventSeverity.CALL_EXCEPTION
                )

                updateNotification("CALL IN PROGRESS", "Microphone temporary exception granted")
            }
        } else {
            // Call Ended! Check if screen is currently off
            repository.recordEvent(
                eventType = "CALL_ENDED",
                title = "Call Ended",
                description = "Voice call finished. Checking screen state to re-evaluate privacy shield.",
                severity = EventSeverity.INFO
            )

            if (currentScreenState == ScreenStateEnum.SCREEN_OFF && settings.autoGuardOnScreenOff) {
                // Re-engage shield because screen is dark!
                applyScreenOffProtection()
            } else {
                updateNotification("SENSOR MONITOR: Idle", "Screen active - Sensors normally accessible")
            }
        }
    }

    private fun toggleManualShield() {
        val isLocked = audioLockManager.isLocked
        if (isLocked) {
            audioLockManager.releaseMicrophoneLock()
            if (SensorGuardAdminReceiver.isAdminActive(this)) {
                SensorGuardAdminReceiver.setCameraDisabled(this, false)
            }
            repository.updateLockState(isMicLocked = false, isCamBlocked = false, isDeviceAdmin = SensorGuardAdminReceiver.isAdminActive(this))
            repository.recordEvent(
                eventType = "MANUAL_OVERRIDE",
                title = "Manual Shield: Released",
                description = "User manually disarmed sensor guard lock.",
                severity = EventSeverity.SHIELD_OFF
            )
            updateNotification("SENSOR MONITOR: Idle", "Manually disarmed by user")
        } else {
            val micLocked = audioLockManager.acquireMicrophoneLock()
            val settings = repository.loadSettings()
            var camBlocked = false
            if (settings.camGuardEnabled && SensorGuardAdminReceiver.isAdminActive(this)) {
                camBlocked = SensorGuardAdminReceiver.setCameraDisabled(this, true)
            }
            repository.updateLockState(isMicLocked = micLocked, isCamBlocked = camBlocked, isDeviceAdmin = SensorGuardAdminReceiver.isAdminActive(this))
            repository.recordEvent(
                eventType = "MANUAL_OVERRIDE",
                title = "Manual Shield: Armed",
                description = "User manually engaged sensor guard lock.",
                severity = EventSeverity.SHIELD_ON
            )
            updateNotification("SHIELD ACTIVE (Manual Lock)", "Microphone manually locked by user")
        }
    }

    private fun buildForegroundNotification(title: String, contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, SensorGuardService::class.java).apply {
            action = ACTION_MANUAL_SHIELD_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SensorGuardService::class.java).apply {
            action = ACTION_STOP_GUARD
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SensorGuardApplication.CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Toggle Shield", togglePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop Guard", stopPendingIntent)
            .build()
    }

    private fun sendPrivacyAlertNotification(title: String, contentText: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SensorGuardApplication.CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        // Use a unique ID based on the title hash or a fixed one. Let's use a dynamic one to stack recent ones briefly,
        // or a fixed one to avoid spam. We will use a fixed ID for simplicity so it just updates the latest alert.
        notificationManager.notify(2001, notification)
    }

    private fun updateNotification(title: String, contentText: String) {
        val notification = buildForegroundNotification(title, contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        audioLockManager.releaseMicrophoneLock()
        if (SensorGuardAdminReceiver.isAdminActive(this)) {
            SensorGuardAdminReceiver.setCameraDisabled(this, false)
        }
        repository.updateLockState(isMicLocked = false, isCamBlocked = false, isDeviceAdmin = SensorGuardAdminReceiver.isAdminActive(this))
        repository.updateServiceState(false)
        repository.recordEvent(
            eventType = "SERVICE_STOPPED",
            title = "SensorGuard Stopped",
            description = "Sensor privacy service was turned off.",
            severity = EventSeverity.WARNING
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerSensorActivityMonitoring() {
        appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        // 1. Android 30+ (R) AppOps active watcher for real OS sensor events
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val ops = arrayOf(
                    AppOpsManager.OPSTR_RECORD_AUDIO,
                    AppOpsManager.OPSTR_CAMERA,
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    AppOpsManager.OPSTR_COARSE_LOCATION
                )
                val listener = AppOpsManager.OnOpActiveChangedListener { op, uid, packageName, active ->
                    if (active) {
                        handleObservedSensorAccess(op, uid, packageName)
                    }
                }
                opActiveChangedListener = listener
                appOpsManager?.startWatchingActive(ops, mainExecutor, listener)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start watching active AppOps: ${e.message}")
            }
        }

        // 2. Android 24+ AudioRecordingCallback for system audio recording configurations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val callback = object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                        super.onRecordingConfigChanged(configs)
                        if (configs.isNotEmpty()) {
                            for (config in configs) {
                                val audioSource = config.clientAudioSource
                                val sourceName = when (audioSource) {
                                    MediaRecorder.AudioSource.MIC -> "Microphone"
                                    MediaRecorder.AudioSource.CAMCORDER -> "Camcorder Mic"
                                    MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "Voice Call / VoIP"
                                    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "Voice Recognition"
                                    else -> "System Audio Session"
                                }
                                handleObservedAudioRecordingSession(sourceName, config.clientAudioSessionId)
                            }
                        }
                    }
                }
                audioRecordingCallback = callback
                audioManager?.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                Log.w(TAG, "Could not register AudioRecordingCallback: ${e.message}")
            }
        }
    }

    private fun handleObservedAudioRecordingSession(sourceName: String, sessionId: Int) {
        val isScreenOn = currentScreenState == ScreenStateEnum.SCREEN_ON || currentScreenState == ScreenStateEnum.SCREEN_UNLOCKED
        val screenStateStr = if (isScreenOn) "ON" else "OFF"
        val settings = repository.loadSettings()

        val enforcementAction = if (!isScreenOn && settings.micGuardEnabled && currentCallState == CallStateEnum.IDLE) {
            EnforcementAction.BLOCKED
        } else {
            EnforcementAction.ALLOWED
        }

        repository.recordLedgerEvent(
            category = LedgerCategory.MICROPHONE,
            triState = TriState.OBSERVED,
            riskLevel = if (enforcementAction == EnforcementAction.BLOCKED) LedgerRisk.WARNING else LedgerRisk.SAFE,
            eventType = "AUDIO_RECORDING_SESSION",
            title = "🎙️ $sourceName Active",
            description = "Active audio capture session (ID: $sessionId). Screen: $screenStateStr.",
            severity = if (enforcementAction == EnforcementAction.BLOCKED) EventSeverity.SHIELD_ON else EventSeverity.INFO,
            enforcementAction = enforcementAction,
            enforcementReason = "OS Audio Session active: $sourceName",
            policyName = if (isScreenOn) "Screen-ON Continuous Mic Monitor" else "Screen-OFF Hardware Mic Guard",
            enforcementResult = "Screen: $screenStateStr | Audio session $sessionId active",
            appName = "System Audio Capture",
            relatedPackage = "android.media",
            screenState = screenStateStr,
            appState = "Active Session",
            permissionState = "Active / Granted",
            policyAction = enforcementAction.name,
            osEnforcement = "OS VERIFIED",
            timestamp = System.currentTimeMillis()
        )
    }

    private fun handleObservedSensorAccess(op: String, uid: Int, pkg: String) {
        if (pkg == packageName) return

        val appLabel = try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            pkg
        }

        val category = when (op) {
            AppOpsManager.OPSTR_RECORD_AUDIO -> LedgerCategory.MICROPHONE
            AppOpsManager.OPSTR_CAMERA -> LedgerCategory.CAMERA
            AppOpsManager.OPSTR_FINE_LOCATION, AppOpsManager.OPSTR_COARSE_LOCATION -> LedgerCategory.LOCATION
            else -> LedgerCategory.SENSORGUARD
        }

        val sensorName = when (category) {
            LedgerCategory.MICROPHONE -> "Microphone"
            LedgerCategory.CAMERA -> "Camera"
            LedgerCategory.LOCATION -> "Location"
            else -> "Sensor"
        }

        val isScreenOn = currentScreenState == ScreenStateEnum.SCREEN_ON || currentScreenState == ScreenStateEnum.SCREEN_UNLOCKED
        val screenStateStr = if (isScreenOn) "ON" else "OFF"

        val isForeground = isUidForeground(uid)
        val appStateStr = if (isForeground) "Foreground" else "Background"

        val settings = repository.loadSettings()

        val enforcementAction: EnforcementAction
        val policyName: String
        val resultStr: String
        val riskLevel: LedgerRisk
        val severity: EventSeverity

        when {
            // Screen OFF and microphone active while guard is armed
            !isScreenOn && category == LedgerCategory.MICROPHONE && settings.micGuardEnabled && currentCallState == CallStateEnum.IDLE -> {
                enforcementAction = EnforcementAction.BLOCKED
                policyName = "Screen-OFF Hardware Mic Guard"
                resultStr = "Screen: OFF | Mic Shield Active | Hardware Locked"
                riskLevel = LedgerRisk.WARNING
                severity = EventSeverity.SHIELD_ON
            }
            // Screen OFF and call exception active
            !isScreenOn && category == LedgerCategory.MICROPHONE && currentCallState != CallStateEnum.IDLE -> {
                enforcementAction = EnforcementAction.ALLOWED
                policyName = "Voice Call Exception"
                resultStr = "Screen: OFF | Call Exception Active | Unlocked for Voice Call"
                riskLevel = LedgerRisk.SAFE
                severity = EventSeverity.CALL_EXCEPTION
            }
            // Camera active and camera disabled via Device Admin
            category == LedgerCategory.CAMERA && settings.camGuardEnabled && SensorGuardAdminReceiver.isAdminActive(this) -> {
                enforcementAction = EnforcementAction.BLOCKED
                policyName = "Device Policy Camera Lock"
                resultStr = "Screen: $screenStateStr | Camera Policy Disabled"
                riskLevel = LedgerRisk.WARNING
                severity = EventSeverity.ALERT
            }
            // Emergency lockdown active
            repository.guardState.value.isEmergencyLockdown -> {
                enforcementAction = EnforcementAction.BLOCKED
                policyName = "Emergency Lockdown Policy"
                resultStr = "Screen: $screenStateStr | Emergency Lockdown Active"
                riskLevel = LedgerRisk.CRITICAL
                severity = EventSeverity.ALERT
            }
            // Screen ON - standard foreground access
            isScreenOn && isForeground -> {
                enforcementAction = EnforcementAction.ALLOWED
                policyName = "Screen-ON Continuous $sensorName Monitor"
                resultStr = "Screen: ON | Foreground | Access observed"
                riskLevel = LedgerRisk.SAFE
                severity = EventSeverity.INFO
            }
            // Screen ON - background access
            isScreenOn && !isForeground -> {
                enforcementAction = EnforcementAction.LIMITED
                policyName = "Screen-ON Background $sensorName Monitor"
                resultStr = "Screen: ON | Background | Access observed"
                riskLevel = LedgerRisk.ATTENTION
                severity = EventSeverity.WARNING
            }
            else -> {
                enforcementAction = EnforcementAction.ALLOWED
                policyName = "Continuous $sensorName Monitor"
                resultStr = "Screen: $screenStateStr | $appStateStr | Access observed"
                riskLevel = LedgerRisk.SAFE
                severity = EventSeverity.INFO
            }
        }

        repository.recordLedgerEvent(
            category = category,
            triState = TriState.OBSERVED,
            riskLevel = riskLevel,
            eventType = "SENSOR_ACCESS_OBSERVED",
            title = "🎙️ $sensorName — $appLabel",
            description = "$sensorName access observed by $appLabel ($pkg). Screen: $screenStateStr, $appStateStr.",
            severity = severity,
            enforcementAction = enforcementAction,
            enforcementReason = "OS Access observed: $sensorName",
            policyName = policyName,
            enforcementResult = resultStr,
            appName = appLabel,
            relatedPackage = pkg,
            screenState = screenStateStr,
            appState = appStateStr,
            permissionState = "Active / Granted",
            policyAction = enforcementAction.name,
            osEnforcement = "OS VERIFIED",
            timestamp = System.currentTimeMillis()
        )

        // Send a system alert notification when microphone or camera is accessed
        if (category == LedgerCategory.MICROPHONE || category == LedgerCategory.CAMERA) {
            val emoji = if (category == LedgerCategory.MICROPHONE) "🎙️" else "📷"
            sendPrivacyAlertNotification(
                title = "$emoji $sensorName Access: $appLabel",
                contentText = "Privacy alert: $appLabel is currently accessing the $sensorName."
            )
        }
    }

    private fun isUidForeground(uid: Int): Boolean {
        return try {
            val am = activityManager ?: return true
            val procs = am.runningAppProcesses ?: return true
            procs.any { it.uid == uid && it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
        } catch (e: Exception) {
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && opActiveChangedListener != null) {
            try {
                appOpsManager?.stopWatchingActive(opActiveChangedListener!!)
            } catch (ignored: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioRecordingCallback != null) {
            try {
                audioManager?.unregisterAudioRecordingCallback(audioRecordingCallback!!)
            } catch (ignored: Exception) {}
        }

        try {
            unregisterReceiver(screenReceiver)
        } catch (ignored: Exception) {}

        try {
            unregisterReceiver(headsetReceiver)
        } catch (ignored: Exception) {}

        try {
            if (networkCallback != null && connectivityManager != null) {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            }
        } catch (ignored: Exception) {}

        try {
            val tm = telephonyManager
            if (tm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                    tm.unregisterTelephonyCallback(telephonyCallback!!)
                } else if (legacyPhoneStateListener != null) {
                    @Suppress("DEPRECATION")
                    tm.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (ignored: Exception) {}

        audioLockManager.releaseMicrophoneLock()
        if (SensorGuardAdminReceiver.isAdminActive(this)) {
            SensorGuardAdminReceiver.setCameraDisabled(this, false)
        }
        repository.updateServiceState(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "SensorGuardService"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_GUARD = "com.example.sensorguard.ACTION_START_GUARD"
        const val ACTION_STOP_GUARD = "com.example.sensorguard.ACTION_STOP_GUARD"
        const val ACTION_MANUAL_SHIELD_TOGGLE = "com.example.sensorguard.ACTION_MANUAL_SHIELD_TOGGLE"
        const val ACTION_EMERGENCY_LOCKDOWN_ON = "com.example.sensorguard.ACTION_EMERGENCY_LOCKDOWN_ON"
        const val ACTION_EMERGENCY_LOCKDOWN_OFF = "com.example.sensorguard.ACTION_EMERGENCY_LOCKDOWN_OFF"

        fun start(context: Context) {
            val intent = Intent(context, SensorGuardService::class.java).apply {
                action = ACTION_START_GUARD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SensorGuardService::class.java).apply {
                action = ACTION_STOP_GUARD
            }
            context.startService(intent)
        }

        fun toggleShield(context: Context) {
            val intent = Intent(context, SensorGuardService::class.java).apply {
                action = ACTION_MANUAL_SHIELD_TOGGLE
            }
            context.startService(intent)
        }

        fun triggerEmergencyLockdown(context: Context) {
            val intent = Intent(context, SensorGuardService::class.java).apply {
                action = ACTION_EMERGENCY_LOCKDOWN_ON
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun disarmEmergencyLockdown(context: Context) {
            val intent = Intent(context, SensorGuardService::class.java).apply {
                action = ACTION_EMERGENCY_LOCKDOWN_OFF
            }
            context.startService(intent)
        }
    }
}
