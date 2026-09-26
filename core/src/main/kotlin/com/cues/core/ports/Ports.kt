package com.cues.core.ports

import com.cues.core.model.ActionId
import com.cues.core.model.ActionArgs
import com.cues.core.model.Capability
import com.cues.core.model.Routine
import com.cues.core.model.ActionState
import com.cues.core.model.OwnedResource
import com.cues.core.model.CleanupObligation
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

    /**
     * Sessions that were still running at [sinceMillis] or began after it:
     * everything whose `endedAtMillis` is null or not before [sinceMillis].
     *
     * Exists so a history screen can read a bounded window instead of every
     * session ever recorded. An implementation may skip, without decoding,
     * any record that has not been written since [sinceMillis] — which means
     * an unfinished session untouched for that long can be absent here.
     * Anything that must see every unfinished session, cleanup included,
     * asks [allUnfinished] instead; this is a window, not an inventory.
     */
    fun recent(sinceMillis: Long): List<Session>
}

/** The result of asking the registry to perform one action. */
data class ActionOutcome(
    val state: ActionState,
    val detail: String? = null,
    /** What this action now makes the session responsible for releasing. */
    val acquired: OwnedResource? = null,
    /**
     * How [state] was confirmed. The engine copies it onto the
     * [com.cues.core.model.ActionRecord] (or, for a release, the obligation)
     * unchanged; it is the executor's claim to make, because only the
     * executor knows whether it re-read anything.
     */
    val verification: com.cues.core.model.Verification = com.cues.core.model.Verification.NONE,
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

    /**
     * Releases one obligation, with the whole session record to hand.
     *
     * This is the overload [com.cues.core.session.SessionEngine] calls. It
     * exists so a release can derive what to restore from durable data —
     * [CleanupObligation.args], the approved action arguments persisted at
     * acquisition — instead of from anything held in memory, which a process
     * death between execute and release would erase.
     *
     * The default delegates to the resource-only overload, so an executor
     * that has not adopted this yet keeps its current behaviour exactly.
     *
     * TODO(app): `AndroidActionExecutor` must override this. For
     * `OwnedResource.UTILITY_CONTRIBUTION` it should take the restore target
     * from `obligation.args as? ActionArgs.UseUtility` (restore = the opposite
     * of `args.state`, for `args.utilityId`), not from its in-memory
     * `utilityRestoreState`; when `args` is null (an obligation recorded
     * before this field existed) it must return `COMPENSATION_FAILED` with
     * "Can't tell what to restore", never `SUCCEEDED`. Its `execute` must also
     * return `Verification.STEPS_CONFIRMED` for `USE_UTILITY` (macro
     * postconditions only), and `READ_BACK` wherever it already re-reads
     * platform state. See plan §10.4 and CLEANUP.md CL-23 item 8.
     */
    fun release(obligation: CleanupObligation, session: Session): ActionOutcome =
        release(obligation.resource, session.id)
}

interface ReceiptSink {
    fun record(sessionId: String, lines: List<String>)
}

/**
 * Persistence for structured receipts, alongside the text ones.
 *
 * Optional, the same way the usage ledger is: a caller that supplies none
 * still gets every text receipt, and nothing that decides behaviour ever
 * reads from here. It exists so Insights and the receipt screens can count
 * and filter by reason code instead of parsing prose.
 */
interface ReceiptLog {
    fun append(record: com.cues.core.receipt.ReceiptRecord)

    /** Records at or after [sinceMillis], oldest first. */
    fun receiptRecords(sinceMillis: Long = 0L): List<com.cues.core.receipt.ReceiptRecord>
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

/**
 * Whether a model-backed drafter can actually be asked right now — never
 * whether it *would* answer well, only whether it should be tried at all.
 *
 * Exists so [com.cues.core.drafting.DifferentialDrafter] can skip a model
 * attempt outright — recorded as [com.cues.core.drafting.AttemptOutcome.SKIPPED_UNAVAILABLE],
 * costing no timeout — instead of asking it, letting it throw or hang, and
 * only then discovering it was never installed or was switched off. Also the
 * one source of truth `:app` renders as "GRAMMAR ONLY" vs "GEMMA ON-DEVICE"
 * (CLEANUP.md: "every draft names the drafter that produced it" — including
 * naming when there wasn't one to ask).
 */
enum class ModelAvailability {
    /** No model file is present at all. */
    NOT_INSTALLED,

    /** A model file exists, but the user has not turned drafting on. */
    INSTALLED_OFF,

    /** Installed and enabled — safe to attempt. */
    READY,
}

/** A live read of [ModelAvailability], the same "ask now, never cache" discipline as [CapabilityProvider]. */
fun interface ModelAvailabilityProbe {
    fun current(): ModelAvailability
}
