package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.SensorGuardApplication
import com.example.data.model.EventSeverity
import com.example.data.model.LedgerCategory
import com.example.data.model.LedgerRisk
import com.example.data.model.TriState

class PackageEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val dataUri = intent.data ?: return
        val packageName = dataUri.schemeSpecificPart ?: return

        // Skip our own app
        if (packageName == context.packageName) return

        val app = context.applicationContext as? SensorGuardApplication ?: return
        val repository = app.repository
        val pm = context.packageManager

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    val appName = try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        packageName
                    }

                    // Check dangerous permissions
                    val permissions = try {
                        val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                        pkgInfo.requestedPermissions ?: emptyArray()
                    } catch (e: Exception) {
                        emptyArray()
                    }

                    val hasMic = permissions.contains(android.Manifest.permission.RECORD_AUDIO)
                    val hasCam = permissions.contains(android.Manifest.permission.CAMERA)
                    val hasLoc = permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                            permissions.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION)

                    val risk = when {
                        hasMic && hasCam -> LedgerRisk.WARNING
                        hasMic || hasCam -> LedgerRisk.ATTENTION
                        else -> LedgerRisk.SAFE
                    }

                    val permSummary = buildList {
                        if (hasMic) add("Microphone")
                        if (hasCam) add("Camera")
                        if (hasLoc) add("Location")
                    }.joinToString(", ").ifEmpty { "Standard baseline" }

                    repository.recordLedgerEvent(
                        category = LedgerCategory.APP_ACTIVITY,
                        triState = TriState.OBSERVED,
                        riskLevel = risk,
                        eventType = "APP_INSTALLED",
                        title = "New Application Installed: $appName",
                        description = "App package '$packageName' was installed. Requested permissions: $permSummary",
                        severity = if (risk == LedgerRisk.WARNING) EventSeverity.WARNING else EventSeverity.INFO,
                        appName = appName,
                        relatedPackage = packageName,
                        appState = "BACKGROUND",
                        permissionState = if (hasMic || hasCam) "DANGEROUS_REQUESTED" else "NORMAL",
                        policyAction = "MONITORED",
                        osEnforcement = "OS_BROADCAST_OBSERVED",
                        forensicNote = "Observed via Android OS Intent.ACTION_PACKAGE_ADDED. Permissions requested at install: $permSummary."
                    )
                }
            }

            Intent.ACTION_PACKAGE_REPLACED -> {
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }
                repository.recordLedgerEvent(
                    category = LedgerCategory.APP_ACTIVITY,
                    triState = TriState.OBSERVED,
                    riskLevel = LedgerRisk.SAFE,
                    eventType = "APP_UPDATED",
                    title = "Application Updated: $appName",
                    description = "Package '$packageName' binaries updated by system installer.",
                    severity = EventSeverity.INFO,
                    appName = appName,
                    relatedPackage = packageName,
                    appState = "BACKGROUND",
                    permissionState = "VERIFIED",
                    policyAction = "MONITORED",
                    osEnforcement = "OS_BROADCAST_OBSERVED",
                    forensicNote = "Observed via Android OS Intent.ACTION_PACKAGE_REPLACED."
                )
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    repository.recordLedgerEvent(
                        category = LedgerCategory.APP_ACTIVITY,
                        triState = TriState.OBSERVED,
                        riskLevel = LedgerRisk.SAFE,
                        eventType = "APP_UNINSTALLED",
                        title = "Application Uninstalled: $packageName",
                        description = "Package '$packageName' was uninstalled and all associated runtime permissions were revoked.",
                        severity = EventSeverity.INFO,
                        appName = packageName,
                        relatedPackage = packageName,
                        appState = "REMOVED",
                        permissionState = "REVOKED",
                        policyAction = "N/A",
                        osEnforcement = "OS_BROADCAST_OBSERVED",
                        forensicNote = "Observed via Android OS Intent.ACTION_PACKAGE_REMOVED."
                    )
                }
            }
        }
    }
}
