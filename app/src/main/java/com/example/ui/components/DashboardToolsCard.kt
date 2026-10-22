package com.example.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.SensorGuardApplication

/** Tools card: the two extra screens and the alert TEST button in one place, same card style and spacing as the other cards. */
@Composable
fun DashboardToolsCard(onOpenTravelChecking: () -> Unit, onOpenSiteBlocking: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var testResult by remember { mutableStateOf("") }
    Card(modifier.fillMaxWidth().testTag("dashboard_tools_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tools", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenTravelChecking, modifier = Modifier.fillMaxWidth().testTag("header_travel_checking")) {
                Text("Travel Checking", maxLines = 1)
            }
            OutlinedButton(onClick = onOpenSiteBlocking, modifier = Modifier.fillMaxWidth().testTag("header_site_blocking")) {
                Text("Block adult and gambling sites", maxLines = 1)
            }
            Button(onClick = { testResult = sendTestAlert(context) }, modifier = Modifier.fillMaxWidth().testTag("test_alert_button")) {
                Text("TEST alert (notification only)", maxLines = 1)
            }
            if (testResult.isNotEmpty()) Text(testResult, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Posts one clearly labelled test notification on the alert channel. Does not touch the microphone, camera or any real event. */
fun sendTestAlert(context: Context): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return "Notifications are off for this app. Allow them in Android settings, then try again."
    return try {
        val n = NotificationCompat.Builder(context, SensorGuardApplication.CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TEST alert")
            .setContentText("This is a test. No app used your microphone or camera.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(2999, n)
        "Test alert sent. Look in the notification shade."
    } catch (e: Exception) {
        "Could not send the test alert."
    }
}
