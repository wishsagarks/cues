package com.cues.app.runtime

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cues.app.CuesApplication
import com.cues.app.MainActivity
import com.cues.core.session.isLive

/** A read-only session control: it stops Cues' current session or opens Cues. */
class CueTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val app = application as CuesApplication
        val session = app.store.allUnfinished().firstOrNull { it.state.isLive() }
        if (session != null) {
            app.cueService.onManualStop(session.id)
            GraceScheduler.cancel(this, session.id)
            refresh()
        } else {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun refresh() {
        val session = (application as CuesApplication).store.allUnfinished().firstOrNull { it.state.isLive() }
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = if (session == null) "No cue running" else "Stop cue"
            subtitle = if (session == null) "Open Cues" else "Tap to stop"
            updateTile()
        }
    }
}
