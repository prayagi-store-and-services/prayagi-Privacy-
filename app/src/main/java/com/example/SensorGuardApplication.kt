package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.repository.PrivacyRepository

class SensorGuardApplication : Application() {

    lateinit var repository: PrivacyRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val database = AppDatabase.getDatabase(this)
        repository = PrivacyRepository(this, database.privacyEventDao())

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "SensorGuard Live Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground active monitoring and sensor privacy guard service"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "SensorGuard Privacy Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority privacy shield alerts, state changes and call exceptions"
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "sensorguard_service_channel"
        const val CHANNEL_ALERTS_ID = "sensorguard_alerts_channel"

        lateinit var instance: SensorGuardApplication
            private set
    }
}
