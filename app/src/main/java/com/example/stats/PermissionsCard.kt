package com.example.stats

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** One permission row: its name, the plain reason it is needed, its live status and what a tap opens. */
data class PermItem(
    val name: String,
    val reason: String,
    val status: (Context) -> String,
    val open: ((Context) -> Unit)?
)

internal fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun runtimeStatus(context: Context, permission: String): String =
    try {
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed"
    } catch (e: Exception) {
        "Unavailable"
    }

internal fun installStatus(context: Context): String =
    try { if (context.packageManager.canRequestPackageInstalls()) "Allowed" else "Not allowed" } catch (e: Exception) { "Unavailable" }

internal fun openInstallSettings(context: Context) {
    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(i) } catch (e: Exception) { context.startActivity(appSettingsIntent(context)) }
}

internal fun notificationStatus(context: Context): String =
    if (android.os.Build.VERSION.SDK_INT >= 33) runtimeStatus(context, android.Manifest.permission.POST_NOTIFICATIONS) else "Allowed"

internal fun adminStatus(context: Context): String = try {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
    if (dpm.isAdminActive(android.content.ComponentName(context, com.example.service.SensorGuardAdminReceiver::class.java))) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

internal fun batteryStatus(context: Context): String = try {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

internal fun appPermissions(): List<PermItem> = listOf(
    PermItem(
        "Microphone",
        "Used to hold the microphone hardware while the screen is off so other apps cannot listen. SensorGuard does not record or send any sound. Tap to open the Android page where you can allow or stop it.",
        { runtimeStatus(it, android.Manifest.permission.RECORD_AUDIO) },
        { it.startActivity(appSettingsIntent(it)) }
    ),
    PermItem(
        "Phone state",
        "Used to notice when a phone call begins or rings, so your microphone is unlocked for the call. The number and the calls are not read. Tap to open the Android page where you can allow or stop it.",
        { runtimeStatus(it, android.Manifest.permission.READ_PHONE_STATE) },
        { it.startActivity(appSettingsIntent(it)) }
    ),
    PermItem(
        "Notifications",
        "Used to keep the guard notice visible while the shield runs and to tell you about guard events. Tap to open the Android page where you can allow or stop it.",
        { notificationStatus(it) },
        { it.startActivity(appSettingsIntent(it)) }
    ),
    PermItem(
        "Device admin",
        "Used only to block the camera at system level. Without it the camera block is not available and only the microphone guard works. Tap to open the Android security page where you can allow or remove it.",
        { adminStatus(it) },
        { try { it.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { it.startActivity(appSettingsIntent(it)) } }
    ),
    PermItem(
        "Ignore battery optimisation",
        "Used so Android does not stop the guard in the background. Tap to open the Android page where you can allow or stop it.",
        { batteryStatus(it) },
        { try { it.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { it.startActivity(appSettingsIntent(it)) } }
    ),
    PermItem(
        "See installed apps",
        "Used by the App audit to list the apps on this phone and which of them ask for the microphone or camera. The list stays on the phone. This is a normal permission, always allowed.",
        { "Always allowed (normal permission)" },
        null
    ),
    PermItem(
        "Install apps",
        "Used only when you tap Install on an update, so Android can install the new SensorGuard file. Tap to open the Android page where you can allow or stop it.",
        { installStatus(it) },
        { openInstallSettings(it) }
    ),
    PermItem(
        "Internet and start after reboot",
        "Internet checks for updates and sends the anonymous usage count if you leave it on. Start after reboot restarts the guard after the phone restarts. Both are normal permissions, always allowed.",
        { "Always allowed (normal permission)" },
        null
    )
)

/** Settings card listing every permission the app uses. Status is read from Android each time the app comes back to the screen; no timer. */
@Composable
fun PermissionsCard(items: List<PermItem>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lc = (context as? ComponentActivity)?.lifecycle
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lc?.addObserver(obs)
        onDispose { lc?.removeObserver(obs) }
    }
    com.example.ui.components.PolicyCard(modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permissions", fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            Text("What the app uses and why. Tap a row to open its Android page.", fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { item ->
                val st = remember(tick) { item.status(context) }
                val m = if (item.open != null) Modifier.fillMaxWidth().clickable { item.open.invoke(context) } else Modifier.fillMaxWidth()
                Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name + "  -  " + st, fontWeight = FontWeight.SemiBold)
                    Text(item.reason, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
