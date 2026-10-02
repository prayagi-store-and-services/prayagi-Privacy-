package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.SensorGuardViewModel

@Composable
fun AppUsesReportScreen(
    viewModel: SensorGuardViewModel,
    modifier: Modifier = Modifier
) {
    val scannedApps by viewModel.scannedApps.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    var selectedReportTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Apps, 2: Permissions, 3: Timeline

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "App Uses Report",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Honest local observation of device capabilities",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.scanApps() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Report",
                        tint = NeonCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Report Sub-Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedReportTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedReportTab == 0,
                    onClick = { selectedReportTab = 0 },
                    text = { Text("Overview") }
                )
                Tab(
                    selected = selectedReportTab == 1,
                    onClick = { selectedReportTab = 1 },
                    text = { Text("App Profile") }
                )
                Tab(
                    selected = selectedReportTab == 2,
                    onClick = { selectedReportTab = 2 },
                    text = { Text("Permissions") }
                )
                Tab(
                    selected = selectedReportTab == 3,
                    onClick = { selectedReportTab = 3 },
                    text = { Text("Timeline") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedReportTab) {
                0 -> ReportOverviewTab(scannedApps, allEvents)
                1 -> ReportAppsTab(scannedApps)
                2 -> ReportPermissionsTab(scannedApps)
                3 -> ReportTimelineTab(allEvents)
            }
        }
    }
}

@Composable
fun ReportOverviewTab(scannedApps: List<com.example.data.model.AppRiskInfo>, allEvents: List<com.example.data.model.PrivacyEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Capability Summary",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StatRow("Total Observed Applications", "${scannedApps.size}")
                    StatRow("Microphone-Bound Apps", "${scannedApps.count { it.hasMicrophonePermission }}")
                    StatRow("Camera-Bound Apps", "${scannedApps.count { it.hasCameraPermission }}")
                    StatRow("Location-Bound Apps", "${scannedApps.count { it.hasLocationPermission }}")
                    StatRow("Logged Permission Events", "${allEvents.size}")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldSafe)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline & Honest Architecture",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All reported observations originate strictly from local device state and permission manifests. No internet or remote telemetry is utilized.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ReportAppsTab(scannedApps: List<com.example.data.model.AppRiskInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(scannedApps) { app ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = app.appName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (app.hasMicrophonePermission) CapabilityBadge("MIC", NeonCyan)
                        if (app.hasCameraPermission) CapabilityBadge("CAMERA", AmberWarning)
                        if (app.hasLocationPermission) CapabilityBadge("LOCATION", EmeraldSafe)
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun ReportPermissionsTab(scannedApps: List<com.example.data.model.AppRiskInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PermissionGroupCard(
                title = "🎤 Microphone Capability",
                apps = scannedApps.filter { it.hasMicrophonePermission }.map { it.appName }
            )
        }
        item {
            PermissionGroupCard(
                title = "📷 Camera Capability",
                apps = scannedApps.filter { it.hasCameraPermission }.map { it.appName }
            )
        }
        item {
            PermissionGroupCard(
                title = "📍 Location Capability",
                apps = scannedApps.filter { it.hasLocationPermission }.map { it.appName }
            )
        }
    }
}

@Composable
fun PermissionGroupCard(title: String, apps: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = NeonCyan)
            Spacer(modifier = Modifier.height(8.dp))
            if (apps.isEmpty()) {
                Text(text = "No applications observed with this capability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                apps.forEach { appName ->
                    Text(text = "• $appName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun ReportTimelineTab(allEvents: List<com.example.data.model.PrivacyEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (allEvents.isEmpty()) {
            item {
                Text(
                    text = "No recorded capability events in timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(allEvents) { event ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = event.appName ?: event.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            Text(text = event.getFormattedTime(), style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Event: ${event.eventType} — ${event.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
