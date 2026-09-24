package com.cues.core.ports

import com.cues.core.model.ActionId
import com.cues.core.model.ActionArgs
import com.cues.core.model.Capability
import com.cues.core.model.Routine
import com.cues.core.model.ActionState
import com.cues.core.model.OwnedResource
import com.cues.core.model.Session
import com.cues.core.model.NamedContext
import com.cues.core.model.Patch
import com.cues.core.model.Place
import com.cues.core.model.Fact
import com.cues.core.model.UiMacro
import com.cues.core.model.UtilityBinding
import com.cues.core.model.UtilityId

/**
 * The seams between decision-making and the world.
 *
 * Everything Cues *decides* lives in :core and is pure. Everything Cues *does*
 * arrives through one of these interfaces, implemented by Android in :app and
 * by fakes in tests. That split is what lets the lifecycle rules — duplicate
 * suppression, reconnect grace, cleanup ordering, crash reconciliation — be
 * tested exhaustively in milliseconds, on any machine, with no phone attached.
 */

/** Wall-clock time, injectable so tests can move time without sleeping. */
fun interface Clock {
    fun nowMillis(): Long
}

interface SessionStore {
    fun save(session: Session)
    fun find(sessionId: String): Session?
    fun activeFor(routineId: String): List<Session>
    fun allUnfinished(): List<Session>
}

/** The result of asking the registry to perform one action. */
data class ActionOutcome(
    val state: ActionState,
    val detail: String? = null,
    /** What this action now makes the session responsible for releasing. */
    val acquired: OwnedResource? = null,
)

/**
 * Performs allowlisted actions. The only path from a routine to the device.
 *
 * Rehearsal is handed a mock implementation of this interface and nothing
 * else, which is why a rehearsal structurally cannot change phone state.
 */
interface ActionExecutor {
    fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome

    /**
     * Releases one owned resource. Must be idempotent: releasing something
     * already released is a success, not an error, because recovery paths will
     * legitimately try twice.
     */
    fun release(resource: OwnedResource, sessionId: String): ActionOutcome
}

interface ReceiptSink {
    fun record(sessionId: String, lines: List<String>)
}

/**
 * Persistence for routines.
 *
 * Separate from [SessionStore] because the two have different failure modes: a
 * lost session is a lost record of one run, a lost routine is a rule the user
 * approved and expects to still be there tomorrow.
 */
interface RoutineStore {
    fun save(routine: Routine)
    // Named findRoutine, not find: a class implementing both RoutineStore and
    // SessionStore cannot have two find(String) overrides whose only
    // difference is an unrelated return type — they erase to the same JVM
    // signature. SessionStore.find has the established call sites, so this
    // is the name that moved.
    fun findRoutine(id: String): Routine?
    fun all(): List<Routine>
    fun armed(): List<Routine>
    fun delete(id: String)
}

interface NamedContextStore {
    fun findContext(id: String): NamedContext?
    fun allContexts(): List<NamedContext>
    fun saveContext(context: NamedContext)
    fun deleteContext(id: String)
}

interface PatchStore {
    fun findPatch(routineId: String): Patch?
    fun savePatch(patch: Patch)
    fun clearPatch(routineId: String)
    fun allPatches(): List<Patch>
}

interface PlaceStore {
    fun findPlace(id: String): Place?
    fun allPlaces(): List<Place>
    fun savePlace(place: Place)
    fun deletePlace(id: String)
}

interface FactStore {
    fun findFact(id: String): Fact?
    fun allFacts(): List<Fact>
    fun saveFact(fact: Fact)
    fun deleteFact(id: String)
}

/** Persistence for taught [UiMacro]s — the on/off pairs behind a [UtilityBinding], and nothing else yet. */
interface MacroStore {
    fun findMacro(id: String): UiMacro?
    fun allMacros(): List<UiMacro>
    fun saveMacro(macro: UiMacro)
    fun deleteMacro(id: String)
}

/** Which taught macro pair, if any, is bound to each cataloged utility. */
interface UtilityBindingStore {
    fun findBinding(utilityId: UtilityId): UtilityBinding?
    fun allBindings(): List<UtilityBinding>
    fun saveBinding(binding: UtilityBinding)
    fun deleteBinding(utilityId: UtilityId)
}

/** Optional semantic ranker. `null` means the on-device model is unavailable. */
fun interface Embedder {
    fun embed(text: String): FloatArray?
}

/**
 * What the OS currently grants.
 *
 * A single read of live permission/access state. [com.cues.core.approval.Approvals]
 * calls this at arm time rather than trusting anything cached, because a user
 * revoking a permission between review and approval is exactly the case this
 * exists to catch.
 */
fun interface CapabilityProvider {
    fun granted(): Set<Capability>
}

/**
 * Whether someone is at the phone right now — screen on and unlocked.
 *
 * The one signal a [com.cues.core.registry.Presence.NEEDS_USER] action is
 * gated on. Defaults to "always present" wherever a caller doesn't wire a
 * real reading (every existing test, the CLI), which preserves today's
 * behaviour for the four actions that predate this port.
 */
fun interface DeviceAttention {
    fun isUserPresent(): Boolean
}

/** One OS-facing signal listener, selected from the signals armed routines need. */
interface SignalAdapter {
    val key: String
    fun start(armed: List<Routine>)
    fun stop()
    fun health(): ListenerHealth
}

data class ListenerHealth(
    val key: String,
    val running: Boolean,
    val detail: String? = null,
)

/**
 * Speech-to-text, kept behind a port so `:core` never depends on Android's
 * `SpeechRecognizer`.
 *
 * No implementation ships this sprint. Speech is the one authoring input
 * whose behaviour genuinely depends on the device and the venue — recognizer
 * availability, downloaded language packs, a loud room — and none of that is
 * knowable without the loaner phone. Typed input already exercises the whole
 * pipeline behind this port; wiring the real recognizer is event work.
 */
interface SpeechInput {
    suspend fun listen(): SpeechResult
}

sealed interface SpeechResult {
    data class Recognized(val transcript: String) : SpeechResult
    data object NoMatch : SpeechResult
    data object PermissionDenied : SpeechResult
    /** The device has no usable recognizer, or the needed language is not downloaded. */
    data object Unavailable : SpeechResult
    data class Failed(val reason: String) : SpeechResult
}
