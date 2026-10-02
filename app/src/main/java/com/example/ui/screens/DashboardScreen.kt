package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallStateEnum
import com.example.data.model.ProtectionStatus
import com.example.data.model.ScreenStateEnum
import com.example.ui.components.DailyEnforcementDashboard
import com.example.ui.components.PulsingShieldWidget
import com.example.ui.components.SensorTelemetryCard
import com.example.ui.components.TransparencyNoticeCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodel.SensorGuardViewModel

@Composable
fun DashboardScreen(
    viewModel: SensorGuardViewModel,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val guardState by viewModel.guardState.collectAsState()
    val events by viewModel.allEvents.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()
    val selectedDateEvents by viewModel.selectedDateEvents.collectAsState()
    val selectedDateText = viewModel.getFormattedSelectedDate()
    val isToday = viewModel.isSelectedDateToday()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("dashboard_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SensorGuard",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Hardware Privacy Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Power Toggle Button
            FilledTonalButton(
                onClick = {
                    if (guardState.isServiceRunning) {
                        viewModel.stopService(context)
                    } else {
                        viewModel.startService(context)
                    }
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (guardState.isServiceRunning) EmeraldSafe.copy(alpha = 0.15f) else CrimsonAlert.copy(alpha = 0.15f),
                    contentColor = if (guardState.isServiceRunning) EmeraldSafe else CrimsonAlert
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("power_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (guardState.isServiceRunning) "ACTIVE" else "OFF",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Pulsing Shield
        PulsingShieldWidget(
            status = guardState.status,
            isMicLocked = guardState.isMicLocked,
            isCamBlocked = guardState.isCamBlocked,
            onClick = {
                if (guardState.isServiceRunning) {
                    viewModel.toggleManualShield(context)
                } else {
                    viewModel.startService(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Emergency Lockdown Panic Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emergency_lockdown_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (guardState.isEmergencyLockdown) CrimsonAlert.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (guardState.isEmergencyLockdown) CrimsonAlert else CrimsonAlert.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CrimsonAlert.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lockdown",
                                tint = CrimsonAlert,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (guardState.isEmergencyLockdown) "EMERGENCY LOCKDOWN ACTIVE" else "Emergency Privacy Panic Lock",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                                color = if (guardState.isEmergencyLockdown) CrimsonAlert else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (guardState.isEmergencyLockdown) "All audio lines isolated immediately" else "One-tap full hardware sensor restriction",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (guardState.isEmergencyLockdown) {
                            viewModel.disarmEmergencyLockdown(context)
                        } else {
                            viewModel.triggerEmergencyLockdown(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("panic_button_toggle"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (guardState.isEmergencyLockdown) EmeraldSafe else CrimsonAlert,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (guardState.isEmergencyLockdown) Icons.Default.Shield else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (guardState.isEmergencyLockdown) "DISARM EMERGENCY LOCKDOWN" else "ENGAGE EMERGENCY LOCKDOWN",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Enforcement Dashboard (Mandatory Component with Date Isolation)
        DailyEnforcementDashboard(
            stats = dailyStats,
            selectedDateText = selectedDateText,
            isToday = isToday,
            onPreviousDay = { viewModel.selectPreviousDay() },
            onNextDay = { viewModel.selectNextDay() },
            onTodayClick = { viewModel.selectToday() },
            eventsForDay = selectedDateEvents,
            onViewAllLedger = onNavigateToLogs
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Protection Analytics & Daily Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Protection Health & Analytics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val statusText = if (!guardState.isServiceRunning) "NOT PROTECTED"
                    else if (!guardState.settings.micGuardEnabled && !guardState.settings.camGuardEnabled) "OBSERVATION ONLY"
                    else if (guardState.isEmergencyLockdown || guardState.isMicLocked || guardState.isCamBlocked) "STRONG"
                    else "MONITORING"

                    val statusColor = when (statusText) {
                        "STRONG" -> EmeraldSafe
                        "MONITORING" -> NeonCyan
                        "OBSERVATION ONLY" -> AmberWarning
                        else -> CrimsonAlert
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                        color = statusColor
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${events.size}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = NeonCyan
                        )
                        Text(
                            text = "Events Logged",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val callExceptions = events.count { it.eventType == "CALL_EXCEPTION" }
                        Text(
                            text = "$callExceptions",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = EmeraldSafe
                        )
                        Text(
                            text = "Call Exceptions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val scheduleStatus = if (guardState.settings.scheduleEnabled) "Active" else "Off"
                        Text(
                            text = scheduleStatus,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = if (guardState.settings.scheduleEnabled) EmeraldSafe else AmberWarning
                        )
                        Text(
                            text = "Schedule Guard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💡 Quick Access: Add the SensorGuard Quick Settings Tile from your Android notification shade for 1-tap toggling anytime.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Telemetry Grid
        Text(
            text = "LIVE SENSOR TELEMETRY",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Microphone Card
            SensorTelemetryCard(
                icon = Icons.Default.Mic,
                title = "Microphone",
                stateText = if (guardState.isMicLocked) "Protected" else "Monitored",
                isActive = guardState.isMicLocked,
                accentColor = if (guardState.isMicLocked) EmeraldSafe else NeonCyan,
                modifier = Modifier.weight(1f).testTag("telemetry_mic")
            )

            // Camera Card
            SensorTelemetryCard(
                icon = Icons.Default.CameraAlt,
                title = "Camera",
                stateText = if (guardState.isCamBlocked) "BLOCKED (Admin)" else "Monitored",
                isActive = guardState.isCamBlocked,
                accentColor = if (guardState.isCamBlocked) EmeraldSafe else NeonCyan,
                modifier = Modifier.weight(1f).testTag("telemetry_cam")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Screen State Card
            SensorTelemetryCard(
                icon = Icons.Default.ScreenLockPortrait,
                title = "Screen",
                stateText = when (guardState.screenState) {
                    ScreenStateEnum.SCREEN_OFF -> "OFF (Dark)"
                    ScreenStateEnum.SCREEN_ON -> "ON (Active)"
                    ScreenStateEnum.SCREEN_UNLOCKED -> "UNLOCKED"
                },
                isActive = guardState.screenState == ScreenStateEnum.SCREEN_OFF,
                accentColor = if (guardState.screenState == ScreenStateEnum.SCREEN_OFF) EmeraldSafe else NeonCyan,
                modifier = Modifier.weight(1f).testTag("telemetry_screen")
            )

            // Phone Call State Card
            SensorTelemetryCard(
                icon = Icons.Default.PhoneInTalk,
                title = "Telephony",
                stateText = when (guardState.callState) {
                    CallStateEnum.IDLE -> "IDLE (No Call)"
                    CallStateEnum.RINGING -> "RINGING (Exception)"
                    CallStateEnum.OFFHOOK -> "ACTIVE CALL"
                },
                isActive = guardState.callState != CallStateEnum.IDLE,
                accentColor = AmberWarning,
                modifier = Modifier.weight(1f).testTag("telemetry_call")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Protection Policies Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sensor Guard Policies",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Auto Shield on Screen Off
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Shield on Screen OFF",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lock microphone automatically when display goes dark",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = guardState.settings.autoGuardOnScreenOff,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings(guardState.settings.copy(autoGuardOnScreenOff = checked))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldSafe,
                            checkedTrackColor = EmeraldSafe.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("switch_auto_guard")
                    )
                }

                Divider(
                    color = CyberBorder.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Call Exception Pass-through
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Call Exception Intelligence",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Release microphone during calls, re-arm when call ends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = guardState.settings.allowCallsException,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings(guardState.settings.copy(allowCallsException = checked))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldSafe,
                            checkedTrackColor = EmeraldSafe.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("switch_call_exception")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System OS Privacy Shortcuts
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System OS Privacy Toggles",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Access Android 12+ Global Microphone & Camera killswitches directly",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.openPrivacyDashboard(context) },
                    modifier = Modifier.fillMaxWidth().testTag("open_privacy_dashboard_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Android Privacy Dashboard")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transparency Card
        TransparencyNoticeCard(
            onLearnMoreClick = onNavigateToSettings
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
