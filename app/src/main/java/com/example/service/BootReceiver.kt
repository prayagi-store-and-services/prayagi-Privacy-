package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.SensorGuardApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? SensorGuardApplication ?: return
            val settings = app.repository.loadSettings()
            if (settings.autoGuardOnScreenOff) {
                val serviceIntent = Intent(context, SensorGuardService::class.java).apply {
                    action = SensorGuardService.ACTION_START_GUARD
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
