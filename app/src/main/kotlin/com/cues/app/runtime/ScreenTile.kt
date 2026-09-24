package com.cues.app.runtime

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cues.app.MainActivity

/**
 * "Cue this screen" — a one-shot, user-invoked read of whatever is currently
 * on screen, handed to the conversation as data (see the FDD's Utility
 * Bindings section). Never runs unless tapped, and nothing it reads is
 * stored past the read itself — [CuesAccessibilityService.captureScreenText]
 * returns the text and keeps none of it.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING — see CLEANUP.md. Whether
 * the accessibility tree still reports the app *behind* the Quick Settings
 * panel as the active window at the moment this tile is tapped, rather than
 * the panel itself, is a real device question this build cannot answer.
 */
class ScreenTile : TileService() {

    override fun onStartListening() {
        val running = CuesAccessibilityService.isRunning()
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            label = "Cue this screen"
            subtitle = if (running) "Read the current screen" else "Enable in Accessibility settings"
            updateTile()
        }
    }

    override fun onClick() {
        val capture = CuesAccessibilityService.captureScreenTextNow()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            (capture as? ScreenCapture.Success)?.let { putExtra(CuesAccessibilityService.EXTRA_SCREEN_CAPTURE, it.text) }
        }
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}
