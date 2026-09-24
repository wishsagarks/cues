package com.cues.app.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cues.core.CueService
import com.cues.core.coach.Detectors
import com.cues.core.cli.ConsoleHtml
import com.cues.core.export.CuesExporter
import com.cues.core.review.forecastToday
import com.cues.app.data.ObservableStore
import java.io.File
import java.time.ZoneId

/**
 * The Desk Bridge's phone-side half: builds the Cue Console as one
 * self-contained HTML file and hands it to the Android share sheet.
 *
 * Office Kit — or any other transport the share sheet offers — carries it
 * from there; nothing here talks to Office Kit directly, and nothing here
 * opens a server. The receiving laptop opens a plain file. See
 * docs/API_VERIFICATION.md and CLEANUP.md for what "Office Kit accepts this"
 * still needs confirming on a loaner.
 */
object ExportImport {

    /**
     * Builds `console.html` from this phone's own live state — the same
     * [CuesExporter] and `console/template.html` the CLI's `./dev console`
     * demo uses, fed real data instead of a fixture.
     */
    fun buildConsoleHtml(context: Context, cueService: CueService, store: ObservableStore): String {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val routines = store.all()
        val forecast = forecastToday(
            routines = routines,
            patches = store.allPatches(),
            snapshot = com.cues.app.runtime.LiveSnapshot.current(context, now),
            zone = zone,
            contexts = store,
        )
        val diagnostics = cueService.diagnostics()

        return ConsoleHtml.render(
            CuesExporter.exportText(
                routines = routines,
                receipts = store.raw.receipts(),
                forecast = forecast,
                coachSuggestions = Detectors.all(store.ledgerEvents(), now),
                ledgerEvents = store.ledgerEvents(),
                primaryDrafter = diagnostics.primaryDrafter.name,
                lastFallbackReason = diagnostics.lastFallbackReason,
                lastInferenceReport = diagnostics.lastInferenceReport,
                generatedAtMillis = now,
            ),
        )
    }

    /** Writes the Console to a shareable cache file and returns a `content://` Uri for it — never a raw `file://`, which Android blocks sharing outside this app. */
    fun writeShareableConsole(context: Context, html: String): Uri {
        val dir = File(context.cacheDir, "shared").also { it.mkdirs() }
        val file = File(dir, "cues-console.html")
        file.writeText(html)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share Cue Console")
    }
}
