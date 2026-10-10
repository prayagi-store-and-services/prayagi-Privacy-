package com.example.ui.viewmodel

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppRiskInfo
import com.example.data.model.EnforcementAction
import com.example.data.model.GuardSettings
import com.example.data.model.LiveGuardState
import com.example.data.model.PrivacyEvent
import com.example.data.model.RiskLevel
import com.example.data.repository.PrivacyRepository
import com.example.service.AppRiskScanner
import com.example.service.SensorGuardAdminReceiver
import com.example.service.SensorGuardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyEnforcementStats(
    val allowed: Int = 0,
    val blocked: Int = 0,
    val limited: Int = 0,
    val restricted: Int = 0,
    val denied: Int = 0,
    val total: Int = 0
)

class SensorGuardViewModel(
    private val repository: PrivacyRepository,
    private val appRiskScanner: AppRiskScanner
) : ViewModel() {

    val guardState: StateFlow<LiveGuardState> = repository.guardState

    val allEvents: StateFlow<List<PrivacyEvent>> = repository.allEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedDashboardDate = MutableStateFlow<Long>(System.currentTimeMillis())
    val selectedDashboardDate: StateFlow<Long> = _selectedDashboardDate.asStateFlow()

    // Strict Date-Isolated Events for the Selected Date
    val selectedDateEvents: StateFlow<List<PrivacyEvent>> = combine(
        allEvents,
        _selectedDashboardDate
    ) { events, selectedTime ->
        val startOfDay = getStartOfDay(selectedTime)
        val endOfDay = getEndOfDay(selectedTime)
        events.filter { it.timestamp in startOfDay..endOfDay }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Strict Date-Isolated Concrete Counters
    val dailyStats: StateFlow<DailyEnforcementStats> = combine(
        selectedDateEvents,
        _selectedDashboardDate
    ) { events, _ ->
        val allowed = events.count { it.enforcementAction == EnforcementAction.ALLOWED }
        val blocked = events.count { it.enforcementAction == EnforcementAction.BLOCKED }
        val limited = events.count { it.enforcementAction == EnforcementAction.LIMITED }
        val restricted = events.count { it.enforcementAction == EnforcementAction.RESTRICTED }
        val denied = events.count { it.enforcementAction == EnforcementAction.DENIED }
        val total = allowed + blocked + limited + restricted + denied
        DailyEnforcementStats(
            allowed = allowed,
            blocked = blocked,
            limited = limited,
            restricted = restricted,
            denied = denied,
            total = total
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailyEnforcementStats()
    )

    private val _scannedApps = MutableStateFlow<List<AppRiskInfo>>(emptyList())
    val scannedApps: StateFlow<List<AppRiskInfo>> = _scannedApps.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    init {
        scanApps()
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun selectToday() {
        _selectedDashboardDate.value = System.currentTimeMillis()
    }

    fun selectPreviousDay() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = _selectedDashboardDate.value
        cal.add(Calendar.DAY_OF_YEAR, -1)
        _selectedDashboardDate.value = cal.timeInMillis
    }

    fun selectNextDay() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = _selectedDashboardDate.value
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val now = System.currentTimeMillis()
        if (cal.timeInMillis > now) {
            _selectedDashboardDate.value = now
        } else {
            _selectedDashboardDate.value = cal.timeInMillis
        }
    }

    fun selectDate(timestamp: Long) {
        _selectedDashboardDate.value = timestamp
    }

    fun isSelectedDateToday(): Boolean {
        return getStartOfDay(_selectedDashboardDate.value) == getStartOfDay(System.currentTimeMillis())
    }

    fun getFormattedSelectedDate(): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return sdf.format(Date(_selectedDashboardDate.value))
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getEndOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun startService(context: Context) {
        SensorGuardService.start(context)
    }

    fun stopService(context: Context) {
        SensorGuardService.stop(context)
    }

    fun toggleManualShield(context: Context) {
        SensorGuardService.toggleShield(context)
    }

    fun updateSettings(newSettings: GuardSettings) {
        repository.updateSettings(newSettings)
    }

    fun completeOnboarding() {
        repository.setOnboardingCompleted(true)
    }

    fun scanApps() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val list = appRiskScanner.scanInstalledApps()
                _scannedApps.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun triggerEmergencyLockdown(context: Context) {
        SensorGuardService.triggerEmergencyLockdown(context)
    }

    fun disarmEmergencyLockdown(context: Context) {
        SensorGuardService.disarmEmergencyLockdown(context)
    }

    fun exportAuditLogs(context: Context) {
        val events = allEvents.value
        val csvData = repository.formatEventsAsCsv(events)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvData)
            putExtra(Intent.EXTRA_TITLE, "${com.example.Brand.name} Privacy Ledger (CSV)")
            putExtra(Intent.EXTRA_SUBJECT, "${com.example.Brand.name} Privacy Ledger CSV Export (${events.size} events)")
            type = "text/csv"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Privacy Ledger (CSV)").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(shareIntent)
    }

    fun exportForensicReport(context: Context) {
        val events = allEvents.value
        val reportData = repository.formatForensicReport(events)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, reportData)
            putExtra(Intent.EXTRA_TITLE, "${com.example.Brand.name} Forensic Privacy Report")
            putExtra(Intent.EXTRA_SUBJECT, "${com.example.Brand.name} Forensic Privacy Ledger Report (${events.size} events)")
            type = "text/plain"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Forensic Ledger Report").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(shareIntent)
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            repository.clearEvents()
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteEvent(id)
        }
    }

    fun openAppDetails(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openPrivacyDashboard(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ignored: Exception) {}
        }
    }

    fun requestDeviceAdmin(activity: Activity) {
        val component = SensorGuardAdminReceiver.getComponentName(activity)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "${com.example.Brand.name} requires Device Admin to enforce hardware Camera disable policy during privacy shield activation."
            )
        }
        activity.startActivity(intent)
    }

    fun removeDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val component = SensorGuardAdminReceiver.getComponentName(context)
        dpm?.removeActiveAdmin(component)
        repository.updateLockState(
            isMicLocked = guardState.value.isMicLocked,
            isCamBlocked = false,
            isDeviceAdmin = false
        )
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(fallback)
            } catch (ignored: Exception) {}
        }
    }

    fun exportJson(events: List<PrivacyEvent>): String = repository.exportAuditLogsJson(events)
    fun exportCsv(events: List<PrivacyEvent>): String = repository.exportAuditLogsCsv(events)

    class Factory(
        private val repository: PrivacyRepository,
        private val appRiskScanner: AppRiskScanner
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SensorGuardViewModel::class.java)) {
                return SensorGuardViewModel(repository, appRiskScanner) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
