package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.EnforcementAction
import com.example.data.model.EventSeverity
import com.example.data.model.LedgerCategory
import com.example.data.model.LedgerRisk
import com.example.data.model.PrivacyEvent
import com.example.data.model.TriState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodel.SensorGuardViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
    viewModel: SensorGuardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val events by viewModel.allEvents.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<LedgerCategory?>(null) }
    var selectedTriStateFilter by remember { mutableStateOf<TriState?>(null) }
    var selectedRiskFilter by remember { mutableStateOf<LedgerRisk?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var inspectingEvent by remember { mutableStateOf<PrivacyEvent?>(null) }

    val filteredEvents = events.filter { event ->
        val matchesSearch = searchQuery.isBlank() ||
                event.title.contains(searchQuery, ignoreCase = true) ||
                event.description.contains(searchQuery, ignoreCase = true) ||
                (event.appName ?: "").contains(searchQuery, ignoreCase = true) ||
                (event.relatedPackage ?: "").contains(searchQuery, ignoreCase = true) ||
                event.eventType.contains(searchQuery, ignoreCase = true) ||
                (event.forensicNote ?: "").contains(searchQuery, ignoreCase = true)

        val matchesCategory = selectedCategoryFilter == null || event.category == selectedCategoryFilter
        val matchesTriState = selectedTriStateFilter == null || event.triState == selectedTriStateFilter
        val matchesRisk = selectedRiskFilter == null || event.riskLevel == selectedRiskFilter

        matchesSearch && matchesCategory && matchesTriState && matchesRisk
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Privacy Activity Ledger") },
            text = { Text("Are you sure you want to delete all recorded forensic privacy events? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAuditLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = CrimsonAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detailed Forensic Inspection Dialog
    inspectingEvent?.let { event ->
        ForensicRecordInspectionDialog(
            event = event,
            onDismiss = { inspectingEvent = null },
            onOpenAppSettings = { pkg ->
                viewModel.openAppDetails(context, pkg)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("event_log_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Privacy Activity Ledger",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Continuous forensic audit ledger · ${events.size} total events",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                Box {
                    IconButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier.testTag("export_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Privacy Ledger",
                            tint = NeonCyan
                        )
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export as CSV Table") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = NeonCyan) },
                            onClick = {
                                showExportMenu = false
                                viewModel.exportAuditLogs(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Forensic Text Report") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = EmeraldSafe) },
                            onClick = {
                                showExportMenu = false
                                viewModel.exportForensicReport(context)
                            }
                        )
                    }
                }

                if (events.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Ledger",
                            tint = CrimsonAlert
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 24-Hour Activity Density Timeline Heatmap
        if (events.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "24-Hour Forensic Activity Heatmap",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonCyan))
                            Text(
                                text = "Live Capture",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 12-slot 2-hour interval activity bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val hours = (0 until 24 step 2)
                        hours.forEach { hour ->
                            val count = events.count {
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
                                val eventHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                eventHour == hour || eventHour == hour + 1
                            }
                            val barHeight = when {
                                count == 0 -> 3.dp
                                count in 1..2 -> 8.dp
                                count in 3..5 -> 14.dp
                                else -> 20.dp
                            }
                            val barColor = when {
                                count == 0 -> MaterialTheme.colorScheme.surface
                                count in 1..2 -> NeonCyan.copy(alpha = 0.6f)
                                count in 3..5 -> EmeraldSafe
                                else -> AmberWarning
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(barColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("00:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("06:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("12:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("18:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("23:59", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by app, event, package, or keyword...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CyberBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("log_search_field")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Category Tabs
        val categoryScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(categoryScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryChip(
                label = "All (${events.size})",
                icon = Icons.Default.Security,
                isSelected = selectedCategoryFilter == null,
                onClick = { selectedCategoryFilter = null }
            )
            CategoryChip(
                label = "Mic (${events.count { it.category == LedgerCategory.MICROPHONE }})",
                icon = Icons.Default.Mic,
                isSelected = selectedCategoryFilter == LedgerCategory.MICROPHONE,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.MICROPHONE) null else LedgerCategory.MICROPHONE }
            )
            CategoryChip(
                label = "Camera (${events.count { it.category == LedgerCategory.CAMERA }})",
                icon = Icons.Default.Videocam,
                isSelected = selectedCategoryFilter == LedgerCategory.CAMERA,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.CAMERA) null else LedgerCategory.CAMERA }
            )
            CategoryChip(
                label = "Location (${events.count { it.category == LedgerCategory.LOCATION }})",
                icon = Icons.Default.LocationOn,
                isSelected = selectedCategoryFilter == LedgerCategory.LOCATION,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.LOCATION) null else LedgerCategory.LOCATION }
            )
            CategoryChip(
                label = "Permissions (${events.count { it.category == LedgerCategory.PERMISSIONS }})",
                icon = Icons.Default.VpnKey,
                isSelected = selectedCategoryFilter == LedgerCategory.PERMISSIONS,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.PERMISSIONS) null else LedgerCategory.PERMISSIONS }
            )
            CategoryChip(
                label = "Apps (${events.count { it.category == LedgerCategory.APP_ACTIVITY }})",
                icon = Icons.Default.Apps,
                isSelected = selectedCategoryFilter == LedgerCategory.APP_ACTIVITY,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.APP_ACTIVITY) null else LedgerCategory.APP_ACTIVITY }
            )
            CategoryChip(
                label = "Shield (${events.count { it.category == LedgerCategory.SENSORGUARD }})",
                icon = Icons.Default.Security,
                isSelected = selectedCategoryFilter == LedgerCategory.SENSORGUARD,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.SENSORGUARD) null else LedgerCategory.SENSORGUARD }
            )
            CategoryChip(
                label = "Calls (${events.count { it.category == LedgerCategory.TELEPHONY }})",
                icon = Icons.Default.PhoneInTalk,
                isSelected = selectedCategoryFilter == LedgerCategory.TELEPHONY,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.TELEPHONY) null else LedgerCategory.TELEPHONY }
            )
            CategoryChip(
                label = "Screen (${events.count { it.category == LedgerCategory.SCREEN }})",
                icon = Icons.Default.Smartphone,
                isSelected = selectedCategoryFilter == LedgerCategory.SCREEN,
                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == LedgerCategory.SCREEN) null else LedgerCategory.SCREEN }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tri-State & Risk Filter Badges
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tri-State Chips
            TriState.values().forEach { tri ->
                FilterChip(
                    selected = selectedTriStateFilter == tri,
                    onClick = { selectedTriStateFilter = if (selectedTriStateFilter == tri) null else tri },
                    label = { Text(tri.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (tri) {
                            TriState.REQUESTED -> AmberWarning.copy(alpha = 0.25f)
                            TriState.OBSERVED -> NeonCyan.copy(alpha = 0.25f)
                            TriState.ENFORCED -> EmeraldSafe.copy(alpha = 0.25f)
                        },
                        selectedLabelColor = when (tri) {
                            TriState.REQUESTED -> AmberWarning
                            TriState.OBSERVED -> NeonCyan
                            TriState.ENFORCED -> EmeraldSafe
                        }
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }

            // Critical Risk Quick Filter
            FilterChip(
                selected = selectedRiskFilter == LedgerRisk.CRITICAL,
                onClick = { selectedRiskFilter = if (selectedRiskFilter == LedgerRisk.CRITICAL) null else LedgerRisk.CRITICAL },
                label = { Text("Critical Alerts", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CrimsonAlert.copy(alpha = 0.25f),
                    selectedLabelColor = CrimsonAlert
                ),
                modifier = Modifier.height(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Ledger Event List
        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (events.isEmpty()) "Continuous Privacy Ledger is active.\nEvents will record in real time." else "No ledger records match current filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEvents, key = { it.id }) { event ->
                    LedgerEventItemCard(
                        event = event,
                        onClick = { inspectingEvent = event },
                        onDeleteClick = { viewModel.deleteLog(event.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) NeonCyan else CyberBorder.copy(alpha = 0.5f)),
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                color = if (isSelected) NeonCyan else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun LedgerEventItemCard(
    event: PrivacyEvent,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val categoryIcon = when (event.category) {
        LedgerCategory.MICROPHONE -> Icons.Default.Mic
        LedgerCategory.CAMERA -> Icons.Default.Videocam
        LedgerCategory.LOCATION -> Icons.Default.LocationOn
        LedgerCategory.PERMISSIONS -> Icons.Default.VpnKey
        LedgerCategory.APP_ACTIVITY -> Icons.Default.Apps
        LedgerCategory.SENSORGUARD -> Icons.Default.Security
        LedgerCategory.TELEPHONY -> Icons.Default.PhoneInTalk
        LedgerCategory.SCREEN -> Icons.Default.Smartphone
    }

    val riskColor = when (event.riskLevel) {
        LedgerRisk.SAFE -> EmeraldSafe
        LedgerRisk.ATTENTION -> NeonCyan
        LedgerRisk.WARNING -> AmberWarning
        LedgerRisk.CRITICAL -> CrimsonAlert
    }

    val triColor = when (event.triState) {
        TriState.REQUESTED -> AmberWarning
        TriState.OBSERVED -> NeonCyan
        TriState.ENFORCED -> EmeraldSafe
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("ledger_item_${event.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(riskColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = riskColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Top Metadata Row: Timestamp (12-hour MS) & Tri-State Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.getFormattedTime(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = NeonCyan
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Enforcement Action Badge
                        if (event.enforcementAction != EnforcementAction.NONE) {
                            val actionBadgeColor = when (event.enforcementAction) {
                                EnforcementAction.ALLOWED -> EmeraldSafe
                                EnforcementAction.BLOCKED -> CrimsonAlert
                                EnforcementAction.LIMITED -> AmberWarning
                                EnforcementAction.RESTRICTED -> NeonCyan
                                EnforcementAction.DENIED -> Color(0xFFFF5252)
                                EnforcementAction.NONE -> Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(actionBadgeColor.copy(alpha = 0.2f))
                                    .border(0.5.dp, actionBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = event.enforcementAction.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = actionBadgeColor
                                )
                            }
                        }

                        // Tri-State Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(triColor.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = event.triState.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = triColor
                            )
                        }

                        // Risk Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(riskColor.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = event.riskLevel.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = riskColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Title
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Description
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )

                // Package / App details
                if (!event.appName.isNullOrBlank() || !event.relatedPackage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "App: ${event.appName ?: "Unknown"} (${event.relatedPackage ?: ""})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NeonCyan.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ForensicRecordInspectionDialog(
    event: PrivacyEvent,
    onDismiss: () -> Unit,
    onOpenAppSettings: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Forensic Record #${event.id}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CyberBorder)

                // Exact Timestamp section
                Text(
                    text = "EXACT FORENSIC TIMESTAMP",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                    color = NeonCyan
                )
                Text(
                    text = event.getFormattedDateTime(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Unix Epoch: ${event.timestamp} ms",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tri-State Distinction Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CATEGORY: ${event.category.name}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "TRI-STATE: ${event.triState.name} · RISK: ${event.riskLevel.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detail Attributes Grid
                Text(
                    text = "FORENSIC CONTEXT MATRIX",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                    color = NeonCyan
                )

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ForensicAttrRow("Enforcement Action", event.enforcementAction.name)
                    if (!event.enforcementReason.isNullOrBlank()) {
                        ForensicAttrRow("Enforcement Reason", event.enforcementReason)
                    }
                    if (!event.policyName.isNullOrBlank()) {
                        ForensicAttrRow("Enforcement Policy", event.policyName)
                    }
                    if (!event.enforcementResult.isNullOrBlank()) {
                        ForensicAttrRow("Enforcement Result", event.enforcementResult)
                    }
                    ForensicAttrRow("Event Action", event.title)
                    ForensicAttrRow("Screen State", event.screenState)
                    ForensicAttrRow("App State", event.appState)
                    ForensicAttrRow("Call State", event.callState)
                    ForensicAttrRow("Permission State", event.permissionState)
                    ForensicAttrRow("Policy Action", event.policyAction)
                    ForensicAttrRow("OS Enforcement", event.osEnforcement)
                }

                // App Package Details
                if (!event.relatedPackage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TARGET APPLICATION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                        color = NeonCyan
                    )
                    Text(
                        text = "${event.appName ?: "Unknown"} (${event.relatedPackage})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Evidence / Forensic Note
                if (!event.forensicNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TECHNICAL EVIDENCE / DISCOVERY NOTE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                        color = NeonCyan
                    )
                    Text(
                        text = event.forensicNote,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!event.relatedPackage.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onOpenAppSettings(event.relatedPackage) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("App Settings")
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Dismiss", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ForensicAttrRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
