package com.cues.app.ui.nav

/**
 * String routes for the 5-tab shell (redesign plan §2). Nav-compose owns the
 * back stack now; the old sealed `Screen` + manual `when` in MainActivity is
 * gone. A `cues://` deep link exists for every one of these — see
 * [CuesDeepLinks] — so the session notification, widget and tiles can each
 * open the right destination instead of MainActivity always landing on Now.
 */
object CuesRoutes {
    // Top-level tabs (bottom nav).
    const val NOW = "now"
    const val INSIGHTS = "insights"
    const val ASK = "ask"
    const val WORKBENCH = "workbench"

    // Pushed flows.
    const val REVIEW = "review" // draft passed via a held-in-memory handle, see ReviewArgHolder
    const val CUE_DETAIL = "cue/{routineId}"
    fun cueDetail(routineId: String) = "cue/$routineId"
    const val CHECKS = "checks"
    const val WORKBENCH_PATCH_BAY = "workbench/patch-bay"
    const val WORKBENCH_CONTEXTS = "workbench/contexts"
    const val WORKBENCH_MEMORY = "workbench/memory"
    const val WORKBENCH_MACROS = "workbench/macros"
    const val INGEST_TIMETABLE = "ingest/timetable"
    const val INGEST_IMPORT = "ingest/import" // result passed via ImportArgHolder
    const val INGEST_SCAN = "ingest/scan"
    const val CUE_CARD_SHARE = "cue-card-share" // routine passed via ReviewArgHolder-style holder

    val bottomTabs = listOf(NOW, INSIGHTS, ASK, WORKBENCH)
}

fun tabTitle(route: String): String = when (route) {
    CuesRoutes.NOW -> "Now"
    CuesRoutes.INSIGHTS -> "Insights"
    CuesRoutes.ASK -> "Ask"
    CuesRoutes.WORKBENCH -> "Workbench"
    else -> "Cues"
}
