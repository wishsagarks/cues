package com.cues.core.store

import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Session
import com.cues.core.model.SessionState
import com.cues.core.model.NamedContext
import com.cues.core.model.Patch
import com.cues.core.model.Place
import com.cues.core.model.Fact
import com.cues.core.model.UiMacro
import com.cues.core.model.UtilityBinding
import com.cues.core.model.UtilityId
import com.cues.core.ports.ReceiptLog
import com.cues.core.ports.ReceiptSink
import com.cues.core.receipt.ReceiptRecord
import com.cues.core.ports.RoutineStore
import com.cues.core.ports.SessionStore
import com.cues.core.ports.NamedContextStore
import com.cues.core.ports.PatchStore
import com.cues.core.ports.PlaceStore
import com.cues.core.ports.FactStore
import com.cues.core.ports.MacroStore
import com.cues.core.ports.UtilityBindingStore
import com.cues.core.coach.CoachState
import com.cues.core.coach.CoachStateStore
import com.cues.core.coach.LedgerEvent
import com.cues.core.coach.UsageLedger
import com.cues.core.inference.InferenceLedger
import com.cues.core.inference.InferenceLedgerEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * File-backed persistence for routines, sessions and receipts.
 *
 * Three properties matter more than the on-disk format:
 *
 *  - **Atomic writes.** Every save goes to a `.tmp` file, then an atomic
 *    rename. A phone killed mid-write must not leave a routine file that
 *    parses as garbage on the next launch.
 *  - **A bad file does not take out the store.** A routine that fails to
 *    deserialize is quarantined — moved aside, logged, and skipped — rather
 *    than thrown. One corrupt cue must not cost the user every other cue they
 *    own.
 *  - **Bounded receipt history.** Unbounded history on a phone is a bug with a
 *    delay, so old receipt files are pruned on write.
 *
 * One directory tree, three subdirectories. A single JSON instance is shared
 * because `Routine` and `Session` reference the same sealed hierarchies
 * (`Trigger`, `Condition`, `ContextValue`), and encoding them consistently
 * matters more than any per-type tuning.
 */
/** One recorded outcome, as the receipt screen wants it: who, when, what happened. */
data class ReceiptEntry(val sessionId: String, val atMillis: Long, val text: String)

class JsonFileStore(
    root: File,
    private val maxReceiptFiles: Int = 200,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    initialSignalOptIn: Boolean = false,
    initialUsageTrackingOptIn: Boolean = false,
) : RoutineStore, SessionStore, ReceiptSink, ReceiptLog, NamedContextStore, PatchStore, PlaceStore, FactStore,
    UsageLedger, CoachStateStore, MacroStore, UtilityBindingStore, InferenceLedger {

    private val routinesDir = File(root, "routines").apply { mkdirs() }
    private val sessionsDir = File(root, "sessions").apply { mkdirs() }
    private val quarantineDir = File(root, "quarantine").apply { mkdirs() }
    private val receiptsDir = File(root, "receipts").apply { mkdirs() }
    private val receiptRecordsDir = File(root, "receipts-v2").apply { mkdirs() }
    private val contextsDir = File(root, "contexts").apply { mkdirs() }
    private val patchesDir = File(root, "patches").apply { mkdirs() }
    private val placesDir = File(root, "places").apply { mkdirs() }
    private val factsDir = File(root, "facts").apply { mkdirs() }
    private val macrosDir = File(root, "macros").apply { mkdirs() }
    private val utilityBindingsDir = File(root, "utility-bindings").apply { mkdirs() }
    private val ledgerDir = File(root, "ledger/events").apply { mkdirs() }
    private val coachStateFile = File(root, "ledger/coach-state.json")
    private val signalOptInFile = File(root, "ledger/signal-opt-in.txt").also { file ->
        if (!file.exists()) file.writeText(initialSignalOptIn.toString())
    }
    private val inferenceLedgerDir = File(root, "ledger/inference").apply { mkdirs() }
    private val usageTrackingOptInFile = File(root, "ledger/usage-tracking-opt-in.txt").also { file ->
        if (!file.exists()) file.writeText(initialUsageTrackingOptIn.toString())
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // -------------------------------------------------------- RoutineStore

    override fun save(routine: Routine) = writeAtomic(File(routinesDir, "${routine.id}.json"), routine)

    override fun findRoutine(id: String): Routine? = readRoutine(File(routinesDir, "$id.json"))

    override fun all(): List<Routine> = routinesDir.listJsonFiles().mapNotNull { readRoutine(it) }

    override fun armed(): List<Routine> = all().filter { it.status == RoutineStatus.ARMED }

    override fun delete(id: String) {
        File(routinesDir, "$id.json").delete()
    }

    // ---------------------------------------------------- contextual state

    override fun findContext(id: String): NamedContext? = readContext(File(contextsDir, "$id.json"))
    override fun allContexts(): List<NamedContext> = contextsDir.listJsonFiles().mapNotNull { readContext(it) }
    override fun saveContext(context: NamedContext) = writeAtomic(File(contextsDir, "${context.id}.json"), context)
    override fun deleteContext(id: String) { File(contextsDir, "$id.json").delete() }

    override fun findPatch(routineId: String): Patch? = readPatch(File(patchesDir, "$routineId.json"))
    override fun allPatches(): List<Patch> = patchesDir.listJsonFiles().mapNotNull { readPatch(it) }
    override fun savePatch(patch: Patch) = writeAtomic(File(patchesDir, "${patch.routineId}.json"), patch)
    override fun clearPatch(routineId: String) { File(patchesDir, "$routineId.json").delete() }

    override fun findPlace(id: String): Place? = readPlace(File(placesDir, "$id.json"))
    override fun allPlaces(): List<Place> = placesDir.listJsonFiles().mapNotNull { readPlace(it) }
    override fun savePlace(place: Place) = writeAtomic(File(placesDir, "${place.id}.json"), place)
    override fun deletePlace(id: String) { File(placesDir, "$id.json").delete() }

    override fun findFact(id: String): Fact? = readFact(File(factsDir, "$id.json"))
    override fun allFacts(): List<Fact> = factsDir.listJsonFiles().mapNotNull { readFact(it) }
    override fun saveFact(fact: Fact) = writeAtomic(File(factsDir, "${fact.id}.json"), fact)
    override fun deleteFact(id: String) { File(factsDir, "$id.json").delete() }

    // ---------------------------------------------------------- MacroStore

    override fun findMacro(id: String): UiMacro? = readMacro(File(macrosDir, "$id.json"))
    override fun allMacros(): List<UiMacro> = macrosDir.listJsonFiles().mapNotNull { readMacro(it) }
    override fun saveMacro(macro: UiMacro) = writeAtomic(File(macrosDir, "${macro.id}.json"), macro)
    override fun deleteMacro(id: String) { File(macrosDir, "$id.json").delete() }

    // ------------------------------------------------- UtilityBindingStore

    override fun findBinding(utilityId: UtilityId): UtilityBinding? =
        readBinding(File(utilityBindingsDir, "${utilityId.name}.json"))
    override fun allBindings(): List<UtilityBinding> = utilityBindingsDir.listJsonFiles().mapNotNull { readBinding(it) }
    override fun saveBinding(binding: UtilityBinding) =
        writeAtomic(File(utilityBindingsDir, "${binding.utilityId.name}.json"), binding)
    override fun deleteBinding(utilityId: UtilityId) { File(utilityBindingsDir, "${utilityId.name}.json").delete() }

    // --------------------------------------------------------- learning data

    override val signalOptIn: Boolean get() = signalOptInFile.readText().trim().toBooleanStrictOrNull() ?: false
    override fun setSignalOptIn(enabled: Boolean) = writeAtomicText(signalOptInFile, enabled.toString())

    override fun appendLedger(event: LedgerEvent) {
        writeAtomic(File(ledgerDir, "${event.atMillis}-${java.util.UUID.randomUUID()}.json"), event)
        val cutoff = nowMillis() - 14L * 86_400_000L
        ledgerDir.listJsonFiles().forEach { file ->
            val value = readLedgerEvent(file)
            if (value == null || value.atMillis < cutoff) file.delete()
        }
    }

    override fun ledgerEvents(): List<LedgerEvent> = ledgerDir.listJsonFiles()
        .mapNotNull { readLedgerEvent(it) }
        .sortedBy { it.atMillis }

    override fun wipeLedger() {
        ledgerDir.listJsonFiles().forEach { it.delete() }
        coachStateFile.delete()
    }

    override fun loadCoachState(): CoachState = readCoachState(coachStateFile) ?: CoachState()
    override fun saveCoachState(state: CoachState) = writeAtomic(coachStateFile, state)

    // ---------------------------------------------------- InferenceLedger

    override val usageTrackingEnabled: Boolean
        get() = usageTrackingOptInFile.readText().trim().toBooleanStrictOrNull() ?: false
    override fun setUsageTrackingEnabled(enabled: Boolean) = writeAtomicText(usageTrackingOptInFile, enabled.toString())

    override fun appendInference(entry: InferenceLedgerEntry) {
        writeAtomic(File(inferenceLedgerDir, "${entry.atMillis}-${java.util.UUID.randomUUID()}.json"), entry)
        // 30 days, deliberately longer than the signal ledger's 14 above —
        // see InferenceLedger's own doc comment for why.
        val cutoff = nowMillis() - 30L * 86_400_000L
        inferenceLedgerDir.listJsonFiles().forEach { file ->
            val value = readInferenceLedgerEntry(file)
            if (value == null || value.atMillis < cutoff) file.delete()
        }
    }

    override fun inferenceEntries(): List<InferenceLedgerEntry> = inferenceLedgerDir.listJsonFiles()
        .mapNotNull { readInferenceLedgerEntry(it) }
        .sortedBy { it.atMillis }

    override fun wipeInferenceLedger() {
        inferenceLedgerDir.listJsonFiles().forEach { it.delete() }
    }

    // -------------------------------------------------------- SessionStore

    /**
     * Writes the session, then stamps the file with this store's clock.
     *
     * The stamp is what [recent] and [pruneSessions] filter on before
     * decoding anything, so it has to be the store's notion of "now" — the
     * same injectable clock the ledger's retention already uses — rather than
     * whatever the filesystem happened to record.
     *
     * Saving a session that has ended is also the moment to prune: it is
     * exactly when the set of prunable sessions can grow, and the check
     * decodes only files already older than the retention window.
     */
    override fun save(session: Session) {
        val file = File(sessionsDir, "${session.id}.json")
        writeAtomic(file, session)
        file.setLastModified(nowMillis())
        if (session.endedAtMillis != null) pruneSessions()
    }

    override fun recent(sinceMillis: Long): List<Session> =
        sessionsDir.listJsonFiles()
            // Coarse filesystems keep whole seconds (FAT keeps two), so the
            // pre-filter is widened slightly; the decoded filter below is exact.
            .filter { it.lastModified() >= sinceMillis - MTIME_SLACK_MILLIS }
            .mapNotNull { readSession(it) }
            .filter { (it.endedAtMillis ?: Long.MAX_VALUE) >= sinceMillis }

    /**
     * Deletes sessions that ended more than [SESSION_RETENTION_MILLIS] ago
     * and owe nothing. Returns how many went.
     *
     * A session with any unreleased obligation is never deleted, however old:
     * the FDD requires cleanup obligations to stay visible until resolved, and
     * the session file is where they live. The same goes for a session with
     * no end recorded — a crash mid-start leaves exactly that, and it is
     * evidence that something may still need releasing.
     */
    fun pruneSessions(): Int {
        val cutoff = nowMillis() - SESSION_RETENTION_MILLIS
        var pruned = 0
        sessionsDir.listJsonFiles()
            // A session that ended before the cutoff was last written no
            // later than its end, unless a cleanup retry touched it since —
            // and then it is not one we would delete anyway.
            .filter { it.lastModified() < cutoff }
            .forEach { file ->
                val session = readSession(file) ?: return@forEach
                if (session.isPrunable(cutoff) && file.delete()) pruned++
            }
        return pruned
    }

    private fun Session.isPrunable(cutoff: Long): Boolean {
        val ended = endedAtMillis ?: return false
        return ended < cutoff &&
            state in FINISHED_STATES &&
            obligations.all { it.released }
    }

    override fun find(sessionId: String): Session? = readSession(File(sessionsDir, "$sessionId.json"))

    override fun activeFor(routineId: String): List<Session> = allSessions().filter { it.routineId == routineId }

    override fun allUnfinished(): List<Session> = allSessions().filter {
        it.state != SessionState.COMPLETED && it.state != SessionState.CANCELLED
    }

    private fun allSessions(): List<Session> = sessionsDir.listJsonFiles().mapNotNull { readSession(it) }

    // -------------------------------------------------------- ReceiptSink

    override fun record(sessionId: String, lines: List<String>) {
        val stamp = System.currentTimeMillis()
        val file = File(receiptsDir, "$stamp-$sessionId.txt")
        writeAtomicText(file, lines.joinToString("\n"))
        pruneReceipts()
    }

    /** Oldest-first, so a UI can just take the last N without re-sorting. */
    fun receiptFiles(): List<File> = (receiptsDir.listFiles() ?: emptyArray())
        .filter { it.extension == "txt" }
        .sortedBy { it.name }

    /**
     * Receipts as structured entries, most recent first — what a UI actually
     * wants, rather than a list of filenames it would have to parse itself.
     */
    fun receipts(limit: Int = maxReceiptFiles): List<ReceiptEntry> =
        receiptFiles().takeLast(limit).reversed().map { file ->
            val name = file.nameWithoutExtension
            // Filenames are "<stamp>-<sessionId>.txt". The stamp is a plain
            // decimal number, so the first '-' is always the real boundary —
            // even though sessionId (a UUID-based id) contains dashes of its
            // own.
            val dash = name.indexOf('-')
            val stamp = if (dash > 0) name.take(dash).toLongOrNull() ?: 0L else 0L
            val sessionId = if (dash >= 0) name.substring(dash + 1) else name
            ReceiptEntry(sessionId, stamp, file.readText())
        }

    private fun pruneReceipts() {
        val files = receiptFiles()
        if (files.size <= maxReceiptFiles) return
        files.take(files.size - maxReceiptFiles).forEach { it.delete() }
    }

    // --------------------------------------------------------- ReceiptLog

    /**
     * Continues numbering from whatever is already on disk, so a restart
     * never reuses a sequence number a surviving file still carries.
     */
    private val receiptRecordSeq = java.util.concurrent.atomic.AtomicLong(
        receiptRecordFiles().mapNotNull { it.name.split('-').getOrNull(1)?.toLongOrNull() }.maxOrNull()?.plus(1) ?: 0L,
    )

    /**
     * One file per record, named `<padded atMillis>-<padded sequence>-<kind>.json`.
     *
     * The padded stamp makes name order time order, so pruning and
     * [receiptRecords]'s window both work from the directory listing alone,
     * without decoding a file they are about to skip. The sequence breaks
     * ties in append order and keeps two receipts from the same instant —
     * a start and the skip it caused for another cue, say — from ever
     * sharing a name.
     */
    override fun append(record: ReceiptRecord) {
        val name = "%015d-%012d-%s.json".format(record.atMillis, receiptRecordSeq.getAndIncrement(), record.kind.name)
        writeAtomic(File(receiptRecordsDir, name), record)
        pruneReceiptRecords()
    }

    override fun receiptRecords(sinceMillis: Long): List<ReceiptRecord> =
        receiptRecordFiles()
            .filter { (it.stampPrefix() ?: Long.MAX_VALUE) >= sinceMillis }
            .mapNotNull { readReceiptRecord(it) }
            .filter { it.atMillis >= sinceMillis }
            .sortedBy { it.atMillis } // stable: equal stamps keep file (append) order

    private fun receiptRecordFiles(): List<File> = receiptRecordsDir.listJsonFiles().sortedBy { it.name }

    /** Same cap, same oldest-first rule, as the text receipts these mirror. */
    private fun pruneReceiptRecords() {
        val files = receiptRecordFiles()
        if (files.size <= maxReceiptFiles) return
        files.take(files.size - maxReceiptFiles).forEach { it.delete() }
    }

    private fun File.stampPrefix(): Long? = name.substringBefore('-').toLongOrNull()

    // ------------------------------------------------------------ helpers

    /**
     * Serializes to a temp file, then renames over the target.
     *
     * `File.renameTo` is atomic within one filesystem on both Linux and
     * Android's internal storage, which is the only place this ever writes.
     * The temp file is written fully and flushed before the rename, so a
     * reader never observes a partial file under this path.
     */
    private inline fun <reified T> writeAtomic(target: File, value: T) =
        writeAtomicText(target, json.encodeToString(value))

    private fun writeAtomicText(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            // Cross-filesystem temp dirs can make rename fail; fall back to a
            // direct write rather than losing the data outright.
            target.writeText(text)
            tmp.delete()
        }
    }

    // Two typed wrappers rather than one reified helper: an inline reified
    // fun's type parameter erases to the same JVM signature for every T, so
    // `find(String): Routine?` and `find(String): Session?` cannot both
    // delegate to one overload without colliding.
    private fun readRoutine(file: File): Routine? = readOrQuarantine(file) { json.decodeFromString(it) }

    private fun readSession(file: File): Session? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readContext(file: File): NamedContext? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readPatch(file: File): Patch? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readPlace(file: File): Place? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readFact(file: File): Fact? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readMacro(file: File): UiMacro? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readBinding(file: File): UtilityBinding? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readLedgerEvent(file: File): LedgerEvent? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readInferenceLedgerEntry(file: File): InferenceLedgerEntry? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readCoachState(file: File): CoachState? = readOrQuarantine(file) { json.decodeFromString(it) }
    private fun readReceiptRecord(file: File): ReceiptRecord? = readOrQuarantine(file) { json.decodeFromString(it) }

    private inline fun <T> readOrQuarantine(file: File, decode: (String) -> T): T? {
        if (!file.isFile) return null
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            quarantine(file, e)
            null
        }
    }

    /**
     * Moves an unreadable file aside rather than deleting it.
     *
     * The content might matter to whoever debugs this later, and the whole
     * point of quarantining instead of throwing is that the rest of the store
     * keeps working while this one record waits for attention.
     */
    private fun quarantine(file: File, cause: Exception) {
        Logger.getLogger("CuesStore").log(Level.WARNING, "Quarantining unreadable file: ${file.name}", cause)
        val dest = File(quarantineDir, "${System.currentTimeMillis()}-${file.name}")
        file.copyTo(dest, overwrite = true)
        file.delete()
    }

    private fun File.listJsonFiles(): List<File> =
        (listFiles() ?: emptyArray()).filter { it.extension == "json" }

    companion object {
        /**
         * How long a finished, fully released session is kept. Thirty days
         * is the longest window Insights reads; nothing older is ever shown.
         */
        const val SESSION_RETENTION_MILLIS: Long = 30L * 86_400_000L

        private const val MTIME_SLACK_MILLIS: Long = 2_000L

        /** Terminal states that owe nothing by themselves. CLEANUP_PENDING is deliberately absent. */
        private val FINISHED_STATES = setOf(SessionState.COMPLETED, SessionState.CANCELLED, SessionState.FAILED)
    }
}
