package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.model.AppRiskInfo
import com.example.data.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRiskScanner(private val context: Context) {

    suspend fun scanInstalledApps(): List<AppRiskInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val appsList = mutableListOf<AppRiskInfo>()

        try {
            val flags = PackageManager.GET_PERMISSIONS
            val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(flags)
            }

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                // Exclude this app itself
                if (pkg.packageName == context.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = appInfo.loadLabel(packageManager).toString()
                val requestedPermissions = pkg.requestedPermissions ?: emptyArray()

                val hasMic = requestedPermissions.contains(Manifest.permission.RECORD_AUDIO)
                val hasCam = requestedPermissions.contains(Manifest.permission.CAMERA)
                val hasLoc = requestedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        requestedPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
                val hasPhone = requestedPermissions.contains(Manifest.permission.READ_PHONE_STATE) ||
                        requestedPermissions.contains(Manifest.permission.PROCESS_OUTGOING_CALLS)
                val hasContacts = requestedPermissions.contains(Manifest.permission.READ_CONTACTS)
                val hasSms = requestedPermissions.contains(Manifest.permission.READ_SMS) ||
                        requestedPermissions.contains(Manifest.permission.RECEIVE_SMS)
                val hasOverlay = requestedPermissions.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)

                // Calculate Risk Score (0 - 100)
                var score = 0
                val riskFactors = mutableListOf<String>()

                if (hasMic) {
                    score += 35
                    riskFactors.add("Microphone Hardware Access")
                }
                if (hasCam) {
                    score += 30
                    riskFactors.add("Camera Sensor Access")
                }
                if (hasLoc) {
                    score += 15
                    riskFactors.add("Precise Location Tracking")
                }
                if (hasPhone) {
                    score += 10
                    riskFactors.add("Phone Call State")
                }
                if (hasContacts) {
                    score += 10
                    riskFactors.add("Contacts Access")
                }
                if (hasSms) {
                    score += 10
                    riskFactors.add("SMS Messages Reading")
                }
                if (hasOverlay) {
                    score += 10
                    riskFactors.add("Display Over Other Apps")
                }

                // If non-system app has both mic and camera, add risk multiplier
                if (!isSystem && hasMic && hasCam) {
                    score += 10
                    riskFactors.add("Simultaneous Audio & Visual Capture")
                }

                score = score.coerceIn(0, 100)

                val riskLevel = when {
                    score >= 70 -> RiskLevel.CRITICAL
                    score >= 50 -> RiskLevel.HIGH
                    score >= 30 -> RiskLevel.MEDIUM
                    score > 0 -> RiskLevel.LOW
                    else -> RiskLevel.MINIMAL
                }

                // Only include apps that have at least one sensor/privacy permission or are non-system
                if (score > 0 || !isSystem) {
                    appsList.add(
                        AppRiskInfo(
                            packageName = pkg.packageName,
                            appName = appName,
                            isSystemApp = isSystem,
                            hasMicrophonePermission = hasMic,
                            hasCameraPermission = hasCam,
                            hasLocationPermission = hasLoc,
                            hasPhoneStatePermission = hasPhone,
                            hasContactsPermission = hasContacts,
                            hasSmsPermission = hasSms,
                            hasOverlayPermission = hasOverlay,
                            riskScore = score,
                            riskLevel = riskLevel,
                            riskFactors = riskFactors
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        appsList.sortedByDescending { it.riskScore }
    }
}
