package com.cues.core.store

import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Session
import com.cues.core.model.SessionState
import com.cues.core.model.NamedContext
import com.cues.core.model.Patch
import com.cues.core.model.Place
import com.cues.core.ports.ReceiptSink
import com.cues.core.ports.RoutineStore
import com.cues.core.ports.SessionStore
import com.cues.core.ports.NamedContextStore
import com.cues.core.ports.PatchStore
import com.cues.core.ports.PlaceStore
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
) : RoutineStore, SessionStore, ReceiptSink, NamedContextStore, PatchStore, PlaceStore {

    private val routinesDir = File(root, "routines").apply { mkdirs() }
    private val sessionsDir = File(root, "sessions").apply { mkdirs() }
    private val quarantineDir = File(root, "quarantine").apply { mkdirs() }
    private val receiptsDir = File(root, "receipts").apply { mkdirs() }
    private val contextsDir = File(root, "contexts").apply { mkdirs() }
    private val patchesDir = File(root, "patches").apply { mkdirs() }
    private val placesDir = File(root, "places").apply { mkdirs() }

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

    // -------------------------------------------------------- SessionStore

    override fun save(session: Session) = writeAtomic(File(sessionsDir, "${session.id}.json"), session)

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
}
