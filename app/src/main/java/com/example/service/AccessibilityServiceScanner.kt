package com.example.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import com.example.data.model.AccessibilityRiskApp

/**
 * S3: finds apps that currently have an accessibility service ENABLED and
 * reports them plainly. Privacy Guard itself uses no accessibility service
 * (removed in v1.0.1); this scanner only reads what the system already knows
 * and never turns anything on or off by itself.
 */
class AccessibilityServiceScanner(private val context: Context) {

    fun scanEnabledAccessibilityApps(): List<AccessibilityRiskApp> {
        val pm = context.packageManager
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return emptyList()
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@mapNotNull null
                    val pkg = serviceInfo.packageName ?: return@mapNotNull null
                    // Privacy Guard itself is excluded; it has no accessibility service anyway.
                    if (pkg == context.packageName) return@mapNotNull null
                    val appInfo = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(pkg, 0)
                        }
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                    val appName = appInfo?.loadLabel(pm)?.toString()
                        ?: serviceInfo.loadLabel(pm)?.toString()
                        ?: pkg
                    val isSystem = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
                    AccessibilityRiskApp(
                        packageName = pkg,
                        appName = appName,
                        isSystemApp = isSystem
                    )
                }
                .distinctBy { it.packageName }
                .sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    companion object {
        /**
         * Plain-language warning for the dashboard card. Returns null when
         * nothing is enabled, so the card stays hidden (no empty UI).
         */
        fun buildWarningSummary(apps: List<AccessibilityRiskApp>): String? {
            if (apps.isEmpty()) return null
            val userInstalled = apps.count { !it.isSystemApp }
            val names = apps.take(4).joinToString(", ") { it.appName }
            val more = if (apps.size > 4) " and ${apps.size - 4} more" else ""
            val lead = if (userInstalled > 0) {
                val noun = if (userInstalled == 1) "app you installed" else "apps you installed"
                "$userInstalled $noun can see and control your screen"
            } else {
                val noun = if (apps.size == 1) "pre-installed app" else "pre-installed apps"
                "${apps.size} $noun can see and control your screen"
            }
            return "$lead: $names$more. Accessibility access lets an app read everything " +
                "on your screen, including passwords and OTPs, and tap buttons for you. " +
                "If you do not recognise an app or do not need it, turn its accessibility access off."
        }
    }
}
