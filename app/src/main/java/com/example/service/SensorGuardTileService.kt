package com.example.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.R
import com.example.SensorGuardApplication
import com.example.data.model.ProtectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class SensorGuardTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var collectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()

        val app = applicationContext as? SensorGuardApplication ?: return
        collectJob?.cancel()
        collectJob = serviceScope.launch {
            app.repository.guardState.collect { state ->
                val tile = qsTile ?: return@collect
                val isActive = state.isServiceRunning && (state.status == ProtectionStatus.PROTECTED || state.status == ProtectionStatus.MONITORING_IDLE)
                
                tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = "SensorGuard"
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = when (state.status) {
                        ProtectionStatus.PROTECTED -> "Shield Armed"
                        ProtectionStatus.MONITORING_IDLE -> "Monitoring"
                        ProtectionStatus.EXCEPTION_CALL -> "Call Exception"
                        ProtectionStatus.PARTIALLY_PROTECTED -> "Partial"
                        ProtectionStatus.DISABLED -> "Off"
                    }
                }
                
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        collectJob?.cancel()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as? SensorGuardApplication ?: return
        val currentState = app.repository.guardState.value

        if (currentState.isServiceRunning) {
            SensorGuardService.toggleShield(this)
        } else {
            SensorGuardService.start(this)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val app = applicationContext as? SensorGuardApplication ?: return
        val state = app.repository.guardState.value

        tile.state = if (state.isServiceRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "SensorGuard"
        tile.updateTile()
    }
}
