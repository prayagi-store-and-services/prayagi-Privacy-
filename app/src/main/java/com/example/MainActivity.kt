package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.service.AppRiskScanner
import com.example.ui.screens.AppAuditScreen
import com.example.ui.screens.AppUsesReportScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EventLogScreen
import com.example.ui.screens.OnboardingWizardScreen
import com.example.ui.screens.PolicySettingsScreen
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodel.SensorGuardViewModel

import com.example.service.SensorGuardService
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: SensorGuardViewModel by viewModels {
        val app = application as SensorGuardApplication
        SensorGuardViewModel.Factory(
            repository = app.repository,
            appRiskScanner = AppRiskScanner(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // By default always active for blocking and monitoring
        try {
            SensorGuardService.start(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MyApplicationTheme {
                SensorGuardApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SensorGuardApp(viewModel: SensorGuardViewModel) {
    val guardState by viewModel.guardState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var forceShowWizard by remember { mutableStateOf(false) }

    val showWizard = !guardState.settings.onboardingCompleted || forceShowWizard

    if (showWizard) {
        OnboardingWizardScreen(
            viewModel = viewModel,
            onComplete = {
                forceShowWizard = false
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Filled.Shield else Icons.Outlined.Shield,
                                contentDescription = "Dashboard"
                            )
                        },
                        label = { Text("Guard") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_item_guard")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Filled.Apps else Icons.Outlined.Apps,
                                contentDescription = "Audit"
                            )
                        },
                        label = { Text("Audit") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_item_audit")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "Ledger"
                            )
                        },
                        label = { Text("Ledger") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_item_logs")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Policy"
                            )
                        },
                        label = { Text("Policy") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_item_policy")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 4) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                                contentDescription = "Report"
                            )
                        },
                        label = { Text("Report") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_item_report")
                    )
                }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_transition",
                modifier = Modifier.padding(innerPadding)
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToLogs = { selectedTab = 2 },
                        onNavigateToSettings = { selectedTab = 3 }
                    )
                    1 -> AppAuditScreen(
                        viewModel = viewModel
                    )
                    2 -> EventLogScreen(
                        viewModel = viewModel
                    )
                    3 -> PolicySettingsScreen(
                        viewModel = viewModel,
                        onRerunWizardClick = { forceShowWizard = true }
                    )
                    4 -> AppUsesReportScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
