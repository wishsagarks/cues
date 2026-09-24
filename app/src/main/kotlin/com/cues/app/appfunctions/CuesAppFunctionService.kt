package com.cues.app.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.cues.app.CuesApplication
import com.cues.app.runtime.GraceScheduler
import com.cues.core.compile.Normalizer
import com.cues.core.drafting.DraftResult
import com.cues.core.model.EventKind
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import com.cues.core.review.ForecastStatus
import com.cues.core.review.ReviewCopy
import com.cues.core.review.forecastToday
import com.cues.core.session.isLive
import java.time.ZoneId

/**
 * Task 17: lets the system's AI agent draft, inspect, start and stop Cues
 * routines without going around this app's own rules. Written from the
 * official guide (`docs/API_VERIFICATION.md`), **never compiled or run** —
 * this environment has no Android SDK, so neither the KSP-generated service
 * class nor the generated `app_metadata`/schema XML this depends on have ever
 * been produced. See CLEANUP.md CL-24.
 *
 * Every function below stays on the authoring side of the trust boundary
 * `docs/FDD.md` describes: [draftCue] only ever produces
 * an unapproved, unarmed draft — the same one a spoken or typed request would
 * — and the user still has to open Cues and approve it there. [startCue] is
 * narrower still: it can only trigger a routine already approved with
 * `Trigger.Manual`, the same "you run it by hand" trigger a user could tap
 * from the app, and CL-28's fix (`TriggerEvent.routineId`) is what makes it
 * safe to name exactly one cue rather than starting every armed manual cue
 * at once. [stopCue] can only release what a session already owns — the same
 * "Stop this session" button already on Routine Detail, reached by id
 * instead of a tap. Nothing here arms a routine, executes an action outside
 * what a routine's own review already disclosed, or grants a permission.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "CuesAppFunctionService",
    appFunctionXmlFileName = "cues_app_function_service",
)
abstract class BaseCuesAppFunctionService : AppFunctionService() {

    private val app: CuesApplication
        get() = applicationContext as CuesApplication

    /**
     * Drafts a cue from a natural-language request, exactly like typing it
     * into Cues. Never arms anything: the result always still needs the
     * user's own approval inside the app.
     *
     * @param params The request, in the user's own words — the same text a
     *   spoken or typed cue would use.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun draftCue(params: DraftCueParams): DraftCueResult =
        when (val result = app.cueService.draft(params.request)) {
            is DraftResult.Drafted -> {
                val normalized = Normalizer.normalize(result.routine)
                // Persisted as an unapproved draft — draftCue has no
                // in-memory screen to hand it to the way the in-app draft
                // flow does, so this is the only way the user can find it
                // again. RoutineDetailScreen's "Review & approve" button
                // (added alongside this file) is what makes it reachable.
                app.store.save(normalized)
                DraftCueResult(
                    drafted = true,
                    title = normalized.title,
                    review = listOf(
                        "When: ${ReviewCopy.whenText(normalized)}",
                        "If: ${ReviewCopy.ifText(normalized)}",
                        "Do: ${ReviewCopy.doText(normalized)}",
                        "Until: ${ReviewCopy.untilText(normalized)}",
                        "Restore: ${ReviewCopy.restoreText(normalized)}",
                    ).joinToString("\n"),
                    problem = null,
                )
            }
            is DraftResult.NeedsClarification -> DraftCueResult(
                drafted = false, title = null, review = null,
                problem = "Cues needs to ask: ${result.question}",
            )
            is DraftResult.Failed -> DraftCueResult(
                drafted = false, title = null, review = null,
                problem = result.reason,
            )
        }

    /**
     * Today's deterministic eligibility forecast — never a prediction, and
     * never a claim about anything that hasn't already been observed. Same
     * text the Today screen renders.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun forecastToday(): ForecastTodayResult {
        val routines = app.cueService.list()
        val forecast = forecastToday(
            routines = routines,
            patches = app.store.allPatches(),
            snapshot = com.cues.app.runtime.LiveSnapshot.current(app),
            zone = ZoneId.systemDefault(),
            contexts = app.store,
        )
        return ForecastTodayResult(
            items = forecast.map { item ->
                val title = routines.firstOrNull { it.id == item.routineId }?.title ?: item.routineId
                ForecastItemResult(
                    cueTitle = title,
                    window = item.window,
                    willArm = item.status == ForecastStatus.WILL_ARM,
                    reasons = item.reasons,
                )
            },
        )
    }

    /**
     * The current, honestly-labelled device context — the same
     * known/unknown values Diagnostics reads, never inferred or guessed for
     * this call.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun currentContext(): CurrentContextResult {
        val snapshot = com.cues.app.runtime.LiveSnapshot.current(app)
        return CurrentContextResult(
            charging = (snapshot.charging as? com.cues.core.model.ContextValue.Known)?.value,
            wifiConnected = (snapshot.wifi as? com.cues.core.model.ContextValue.Known)?.value?.connected,
            batteryPercent = (snapshot.batteryPercent as? com.cues.core.model.ContextValue.Known)?.value,
        )
    }

    /**
     * Starts a routine that already runs "by hand" — never a routine armed
     * by a device event, a schedule or any other trigger, and never a cue
     * that isn't already approved and armed. This is the one function here
     * that executes rather than only proposing, so it is deliberately the
     * narrowest: it can do only what a `Trigger.Manual` cue's own review
     * already disclosed, the same as if the user had a "run now" button and
     * tapped it themselves.
     *
     * @param params Names exactly which manual cue to run.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun startCue(params: StartCueParams): StartCueResult {
        val routine = app.cueService.list().firstOrNull { it.id == params.routineId }
            ?: throw AppFunctionElementNotFoundException("No cue with id ${params.routineId}.")
        if (routine.trigger !is Trigger.Manual) {
            throw AppFunctionInvalidArgumentException(
                "\"${routine.title}\" doesn't run by hand — only manually-triggered cues can be started this way.",
            )
        }
        if (routine.status != RoutineStatus.ARMED) {
            throw AppFunctionInvalidArgumentException(
                "\"${routine.title}\" is ${routine.status.name.lowercase()}, not armed.",
            )
        }
        val results = app.cueService.onDeviceEvent(
            TriggerEvent(EventKind.MANUAL_RUN, System.currentTimeMillis(), routineId = routine.id),
        )
        val started = results.filterIsInstance<com.cues.core.session.EngineResult.Started>().any()
        return StartCueResult(
            started = started,
            message = if (started) "Started \"${routine.title}\"." else "\"${routine.title}\" did not start — its conditions weren't met.",
        )
    }

    /**
     * Stops one cue's currently running session — exactly the "Stop this
     * session" control on Routine Detail, reached by a routine id instead of
     * a tap. Releases only what that session owns; nothing else changes.
     *
     * @param params Names which cue's active session to stop.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun stopCue(params: StopCueParams): StopCueResult {
        val routine = app.cueService.list().firstOrNull { it.id == params.routineId }
            ?: throw AppFunctionElementNotFoundException("No cue with id ${params.routineId}.")
        val sessionId = app.store.activeFor(routine.id).firstOrNull { it.state.isLive() }?.id
            ?: return StopCueResult(stopped = false, message = "\"${routine.title}\" has no running session.")
        val result = app.cueService.onManualStop(sessionId)
        GraceScheduler.cancel(app, sessionId)
        return if (result != null) {
            StopCueResult(stopped = true, message = "Stopped \"${routine.title}\".")
        } else {
            throw AppFunctionInvalidArgumentException("Could not stop \"${routine.title}\".")
        }
    }
}

@AppFunctionSerializable(isDescribedByKDoc = true)
data class DraftCueParams(
    /** What the user asked for, in their own words. */
    val request: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class DraftCueResult(
    /** False when Cues could not draft this request at all — see [problem]. */
    val drafted: Boolean,
    /** The drafted cue's title, when [drafted] is true. */
    val title: String?,
    /** Cues' own WHEN/IF/DO/UNTIL/RESTORE review text, never a paraphrase. */
    val review: String?,
    /** A clarifying question or a plain limitation, when [drafted] is false. */
    val problem: String?,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class ForecastItemResult(
    /** The cue's own title, never its internal id. */
    val cueTitle: String,
    val window: String,
    val willArm: Boolean,
    val reasons: List<String>,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class ForecastTodayResult(
    val items: List<ForecastItemResult>,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class CurrentContextResult(
    /** Null means unknown, never a guessed false. */
    val charging: Boolean?,
    val wifiConnected: Boolean?,
    val batteryPercent: Int?,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class StartCueParams(
    val routineId: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class StartCueResult(
    val started: Boolean,
    val message: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class StopCueParams(
    val routineId: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class StopCueResult(
    val stopped: Boolean,
    val message: String,
)
