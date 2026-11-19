package com.domm.projectreplay

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ReplayTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean("recording", false)) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
        } else {
            val launch = Intent(this, CapturePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
        qsTile?.state = if (prefs.getBoolean("recording", false)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        syncTile()
    }

    private fun syncTile() {
        val active = getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE).getBoolean("recording", false)
        qsTile?.label = if (active) "Stop Replay" else "Start Replay"
        qsTile?.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}
