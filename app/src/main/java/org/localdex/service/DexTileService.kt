package org.localdex.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class DexTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(DexSessionService.sessionState.value)

        stateJob = scope.launch {
            DexSessionService.sessionState.collect { state ->
                updateTileState(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateJob?.cancel()
    }

    override fun onClick() {
        super.onClick()
        val currentState = DexSessionService.sessionState.value
        if (currentState is SessionState.Running) {
            val stopIntent = Intent(this, DexSessionService::class.java).apply {
                action = DexSessionService.ACTION_STOP_SESSION
            }
            startService(stopIntent)
        } else {
            // Open MainActivity to initiate or select profile
            val appIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appIntent?.let { startActivityAndCollapse(it) }
        }
    }

    private fun updateTileState(state: SessionState) {
        val tile = qsTile ?: return
        when (state) {
            is SessionState.Running -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "LocalDeX Active (#${state.sessionInfo.displayId})"
                tile.subtitle = "${state.sessionInfo.profile.width}x${state.sessionInfo.profile.height}"
            }
            is SessionState.Starting -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Starting DeX..."
                tile.subtitle = null
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "LocalDeX"
                tile.subtitle = "Tap to launch"
            }
        }
        tile.updateTile()
    }
}
