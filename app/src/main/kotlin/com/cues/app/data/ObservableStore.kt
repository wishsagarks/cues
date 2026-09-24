package com.cues.app.data

import com.cues.core.coach.CoachState
import com.cues.core.coach.CoachStateStore
import com.cues.core.coach.LedgerEvent
import com.cues.core.coach.UsageLedger
import com.cues.core.model.Fact
import com.cues.core.model.NamedContext
import com.cues.core.model.Patch
import com.cues.core.model.Place
import com.cues.core.model.Routine
import com.cues.core.model.Session
import com.cues.core.model.UiMacro
import com.cues.core.model.UtilityBinding
import com.cues.core.model.UtilityId
import com.cues.core.ports.FactStore
import com.cues.core.ports.MacroStore
import com.cues.core.ports.NamedContextStore
import com.cues.core.ports.PatchStore
import com.cues.core.ports.PlaceStore
import com.cues.core.ports.ReceiptLog
import com.cues.core.ports.ReceiptSink
import com.cues.core.ports.RoutineStore
import com.cues.core.ports.SessionStore
import com.cues.core.ports.UtilityBindingStore
import com.cues.core.store.JsonFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Disk is the truth; this is only a hint that something changed.
 *
 * OriginOS can kill this process between a background session start (a
 * Bluetooth receiver, [com.cues.app.runtime.SessionService]) and the next
 * time the UI opens — there is no `android:process` split, but a *process*,
 * not a component, is what the OS actually reclaims. Any purely in-memory
 * event a screen "missed" while stopped would show stale state after such a
 * kill. So this is a [StateFlow], not a one-shot event bus: a [StateFlow]
 * always holds its latest value, and every screen re-reads the relevant files
 * from scratch on each collection start (`ON_START`, and fresh at generation
 * 0 in a brand new process) rather than trusting anything cached across a
 * stop. See the redesign plan §10.1.
 */
class StoreGeneration {
    private val _value = MutableStateFlow(0L)
    val value: StateFlow<Long> = _value.asStateFlow()

    /** Called after every write that a screen might care about. */
    fun bump() {
        _value.value += 1
    }
}

/**
 * Wraps [JsonFileStore] and bumps a [StoreGeneration] after every write, by
 * delegating every read straight through (`by delegate`) and overriding only
 * the methods that mutate something on disk.
 *
 * One wrapper for every store port `JsonFileStore` implements, rather than a
 * decorator per interface: `JsonFileStore` is one object backing all of them,
 * so one generation counter for "something on disk changed" is the honest
 * granularity — a screen that cares about a narrower change still just
 * re-reads its own files, which is cheap (see the pruning/benchmark work in
 * `:core`'s `Insights`).
 */
class ObservableStore(
    private val delegate: JsonFileStore,
    private val generation: StoreGeneration,
) : RoutineStore by delegate,
    SessionStore by delegate,
    ReceiptSink by delegate,
    ReceiptLog by delegate,
    NamedContextStore by delegate,
    PatchStore by delegate,
    PlaceStore by delegate,
    FactStore by delegate,
    MacroStore by delegate,
    UtilityBindingStore by delegate,
    UsageLedger by delegate,
    CoachStateStore by delegate {

    // ---- RoutineStore -----------------------------------------------------
    override fun save(routine: Routine) {
        delegate.save(routine)
        generation.bump()
    }

    override fun delete(id: String) {
        delegate.delete(id)
        generation.bump()
    }

    // ---- SessionStore -------------------------------------------------------
    // save(Session) collides on erasure with RoutineStore.save(Routine)? No —
    // different parameter types, so both overrides are distinct JVM methods.
    override fun save(session: Session) {
        delegate.save(session)
        generation.bump()
    }

    // ---- ReceiptSink --------------------------------------------------------
    override fun record(sessionId: String, lines: List<String>) {
        delegate.record(sessionId, lines)
        generation.bump()
    }

    // ---- ReceiptLog -----------------------------------------------------------
    override fun append(record: com.cues.core.receipt.ReceiptRecord) {
        delegate.append(record)
        generation.bump()
    }

    // ---- NamedContextStore --------------------------------------------------
    override fun saveContext(context: NamedContext) {
        delegate.saveContext(context)
        generation.bump()
    }

    override fun deleteContext(id: String) {
        delegate.deleteContext(id)
        generation.bump()
    }

    // ---- PatchStore -----------------------------------------------------------
    override fun savePatch(patch: Patch) {
        delegate.savePatch(patch)
        generation.bump()
    }

    override fun clearPatch(routineId: String) {
        delegate.clearPatch(routineId)
        generation.bump()
    }

    // ---- PlaceStore -----------------------------------------------------------
    override fun savePlace(place: Place) {
        delegate.savePlace(place)
        generation.bump()
    }

    override fun deletePlace(id: String) {
        delegate.deletePlace(id)
        generation.bump()
    }

    // ---- FactStore ------------------------------------------------------------
    override fun saveFact(fact: Fact) {
        delegate.saveFact(fact)
        generation.bump()
    }

    override fun deleteFact(id: String) {
        delegate.deleteFact(id)
        generation.bump()
    }

    // ---- MacroStore -----------------------------------------------------------
    override fun saveMacro(macro: UiMacro) {
        delegate.saveMacro(macro)
        generation.bump()
    }

    override fun deleteMacro(id: String) {
        delegate.deleteMacro(id)
        generation.bump()
    }

    // ---- UtilityBindingStore ----------------------------------------------------
    override fun saveBinding(binding: UtilityBinding) {
        delegate.saveBinding(binding)
        generation.bump()
    }

    override fun deleteBinding(utilityId: UtilityId) {
        delegate.deleteBinding(utilityId)
        generation.bump()
    }

    // ---- UsageLedger ------------------------------------------------------------
    override fun setSignalOptIn(enabled: Boolean) {
        delegate.setSignalOptIn(enabled)
        generation.bump()
    }

    override fun appendLedger(event: LedgerEvent) {
        delegate.appendLedger(event)
        generation.bump()
    }

    override fun wipeLedger() {
        delegate.wipeLedger()
        generation.bump()
    }

    // ---- CoachStateStore --------------------------------------------------------
    override fun saveCoachState(state: CoachState) {
        delegate.saveCoachState(state)
        generation.bump()
    }

    /** The wrapped store, for callers that need a concrete [JsonFileStore] extra (`receipts(limit)`, `receiptFiles()`). */
    val raw: JsonFileStore get() = delegate
}
