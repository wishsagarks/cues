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

    /**
     * Redesign plan §5.3: STATE_ACTIVE means "the thing this tile controls
     * is currently on" — that's a live session, not the tile's own
     * availability, which was the pre-redesign behaviour (always ACTIVE).
     * The subtitle names the actual cue rather than a generic "cue running".
     */
    private fun refresh() {
        val app = application as CuesApplication
        val session = app.store.allUnfinished().firstOrNull { it.state.isLive() }
        val routineTitle = session?.let { s -> app.store.findRoutine(s.routineId)?.title }
        qsTile?.apply {
            state = if (session == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            label = if (session == null) "Cues" else "Stop \"${routineTitle ?: "cue"}\""
            subtitle = if (session == null) "No cue running" else "Tap to stop"
            updateTile()
        }
    }
}
