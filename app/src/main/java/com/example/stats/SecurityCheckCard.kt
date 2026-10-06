package com.example.stats

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Result of the one-time check done when the card is opened. Nothing runs in the background. */
data class SecurityCheck(
    val patchDate: String?,
    val patchAgeDays: Long?,
    val installApps: List<String>?
)

/** Our own warning line: Android publishes a patch about every month, so over this many days we say the phone looks behind. */
const val PATCH_WARN_DAYS = 90L

internal fun patchAgeDays(patch: String?, nowMillis: Long): Long? {
    if (patch.isNullOrBlank()) return null
    return try {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        val d = f.parse(patch) ?: return null
        val days = (nowMillis - d.time) / 86_400_000L
        if (days < 0) null else days
    } catch (e: Exception) {
        null
    }
}

/** Apps (not system apps) that ask to install other apps AND are allowed to right now. Null when Android gives no way to tell. */
internal fun installAllowedApps(context: Context): List<String>? {
    if (Build.VERSION.SDK_INT < 29) return null
    return try {
        val pm = context.packageManager
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val out = mutableListOf<String>()
        for (info in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            val ai = info.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (info.packageName == context.packageName) continue
            val asks = info.requestedPermissions?.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES) == true
            if (!asks) continue
            val mode = ops.unsafeCheckOpNoThrow("android:request_install_packages", ai.uid, info.packageName)
            if (mode == AppOpsManager.MODE_ALLOWED) out.add(pm.getApplicationLabel(ai).toString())
        }
        out.sorted()
    } catch (e: Exception) {
        null
    }
}

/** Settings card: security patch age and apps allowed to install other apps. Read once when opened. */
@Composable
fun SecurityCheckCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var check by remember { mutableStateOf<SecurityCheck?>(null) }
    LaunchedEffect(Unit) {
        check = withContext(Dispatchers.Default) {
            val patch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else null
            SecurityCheck(patch, patchAgeDays(patch, System.currentTimeMillis()), installAllowedApps(context))
        }
    }
    com.example.ui.components.PolicyCard(modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Phone security check", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Read once when you open this screen, from Android itself. Nothing runs in the background and nothing is sent anywhere.",
                fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val c = check
            if (c == null) {
                Text("Checking...", fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            } else {
                val patchLine = when {
                    c.patchDate.isNullOrBlank() || c.patchAgeDays == null -> "Security patch: Unavailable"
                    c.patchAgeDays > PATCH_WARN_DAYS -> "Security patch: " + c.patchDate + " (" + c.patchAgeDays + " days old). Warning: more than " + PATCH_WARN_DAYS + " days behind. Check Android system update."
                    else -> "Security patch: " + c.patchDate + " (" + c.patchAgeDays + " days old)"
                }
                Text(patchLine, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                val apps = c.installApps
                val installLine = when {
                    apps == null -> "Apps allowed to install other apps: Unavailable on this Android version"
                    apps.isEmpty() -> "Apps allowed to install other apps: none"
                    else -> "Warning: " + apps.size + " app(s) are allowed to install other apps: " + apps.take(8).joinToString(", ") + (if (apps.size > 8) " and " + (apps.size - 8) + " more" else "") + ". Tap to review and switch off any you do not need."
                }
                Text(
                    installLine, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            context.startActivity(appSettingsIntent(context))
                        }
                    }
                )
            }
        }
    }
}
