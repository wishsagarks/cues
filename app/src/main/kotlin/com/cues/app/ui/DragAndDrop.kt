package com.cues.app.ui

import android.content.ClipData
import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Long-press to start a system drag carrying plain text — OriginOS's Atomic
 * Workbench (drag between two apps shown side by side) reads this the same
 * way any Android split-screen target does; nothing here is OriginOS-specific.
 *
 * Uses the platform [View.startDragAndDrop] directly rather than Compose's
 * `Modifier.dragAndDropSource`: that modifier's callback shape changed
 * between Compose Foundation releases, and this build has no compiler
 * available to confirm which shape the pinned version expects (see
 * CLEANUP.md). The `View` API has been stable, unchanged, since API 24 —
 * verifiable from documentation alone, which is what this pass can actually
 * check without a device.
 *
 * [view] should be the root Compose host view ([androidx.compose.ui.platform.LocalView]);
 * the drag shadow is a snapshot of that whole view, not just this
 * composable's bounds — a real, disclosed visual limitation, not a bug.
 *
 * WRITTEN AGAINST A REAL API, VERIFIED ON NOTHING — same caveat as every
 * other `runtime`/`ui` class touching a real Android API in this repo; see
 * CLEANUP.md.
 */
fun Modifier.dragAndDropTextSource(view: View, label: String, textProvider: () -> String): Modifier =
    this.pointerInput(view) {
        detectTapGestures(
            onLongPress = {
                val clipData = ClipData.newPlainText(label, textProvider())
                // startDragAndDrop (API 24+), not the deprecated startDrag.
                view.startDragAndDrop(clipData, View.DragShadowBuilder(view), null, View.DRAG_FLAG_GLOBAL)
            },
        )
    }
