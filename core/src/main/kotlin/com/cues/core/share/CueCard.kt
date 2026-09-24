package com.cues.core.share

import com.cues.core.compile.Normalizer
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.ActionSpec
import com.cues.core.model.CleanupPolicy
import com.cues.core.model.Condition
import com.cues.core.model.DraftSourceId
import com.cues.core.model.EndCondition
import com.cues.core.model.NamedContext
import com.cues.core.model.Place
import com.cues.core.model.RearmPolicy
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.SCHEMA_VERSION
import com.cues.core.model.Trigger
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The offline, portable shape of one routine — everything needed to propose
 * the same behaviour on another phone, and nothing that would let it arm
 * itself there.
 *
 * Deliberately excludes [Routine.approvedDigest], [Routine.status] and
 * [Routine.requiredCapabilities]: an approval is a promise this phone's user
 * made, not a fact that travels with the data, and capabilities are always
 * re-derived from the registry on import, never accepted from another
 * phone's claim about itself — the same rule [Normalizer.normalize] already
 * applies to a routine drafted locally.
 *
 * Device, place and context references travel as *labels*.
 * [Trigger.BluetoothConnection], [Trigger.PlaceTransition],
 * [Condition.DeviceConnected], [Condition.InContext] and [Condition.AtPlace]
 * already carry a human-readable label alongside their id — the id and
 * version embedded in [trigger] and [conditions] here are the *sender's own*
 * and are discarded on import; [CueCards.reimport] re-resolves every one of
 * them against the receiving phone's own paired devices, places and named
 * contexts by that label, and refuses the import outright if one is not
 * found there. A card is never armed directly: [CueCards.reimport] always
 * returns a fresh [RoutineStatus.DRAFT] for the ordinary Review screen.
 */
@Serializable
data class CueCard(
    val cardSchemaVersion: Int = CueCards.CURRENT_CARD_SCHEMA,
    val schemaVersion: Int,
    val title: String,
    val sourceText: String,
    val trigger: Trigger,
    val conditions: List<Condition>,
    val actions: List<ActionSpec>,
    val endConditions: List<EndCondition>,
    val cleanupPolicy: CleanupPolicy,
    val rearmPolicy: RearmPolicy,
    /**
     * SHA-256 over every field above in this same canonical JSON form, with
     * this field itself held blank — computed by the sender in [CueCards.from],
     * and the very first thing [CueCards.decode] checks, before any
     * resolution is attempted.
     */
    val digest: String = "",
)

/** What came back from reading a card someone shared, before any device/place/context resolution. */
sealed interface CardDecodeResult {
    data class Ok(val card: CueCard) : CardDecodeResult
    /** The digest does not match its own content — corrupted in transit, or deliberately edited. Never resolved further. */
    data object Tampered : CardDecodeResult
    data class Malformed(val reason: String) : CardDecodeResult
}

/** What came back from turning a card into something this phone can review. */
sealed interface ReimportResult {
    data class Ready(val routine: Routine) : ReimportResult
    /** [kind] and [label] name exactly what this phone does not have, so the user can pair or declare it before retrying. */
    data class MissingEntity(val kind: String, val label: String) : ReimportResult
    data object Tampered : ReimportResult
    data class Malformed(val reason: String) : ReimportResult
    data class UnsupportedSchema(val cardSchema: Int) : ReimportResult
}

/**
 * Encodes, decodes and reimports [CueCard]s.
 *
 * The whole point of this object is the boundary it enforces: [from] never
 * reads anything a phone would need to keep private (no approval, no live
 * capability grant), and [reimport] never trusts anything a phone would need
 * to verify for itself (a digest, a device id, a capability) — it recomputes
 * or re-resolves every one of them locally.
 */
object CueCards {
    const val CURRENT_CARD_SCHEMA = 1

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /** Builds a card from an approved-or-not routine — approval status itself never travels. */
    fun from(routine: Routine): CueCard {
        val blank = CueCard(
            schemaVersion = routine.schemaVersion,
            title = routine.title,
            sourceText = routine.sourceText,
            trigger = routine.trigger,
            conditions = routine.conditions,
            actions = routine.actions,
            endConditions = routine.endConditions,
            cleanupPolicy = routine.cleanupPolicy,
            rearmPolicy = routine.rearmPolicy,
            digest = "",
        )
        return blank.copy(digest = digestOf(blank))
    }

    /** The exact text a QR code, a `.cuecard` file or a share-sheet message carries. */
    fun encode(card: CueCard): String = json.encodeToString(card)

    fun decode(text: String): CardDecodeResult {
        val card = try {
            json.decodeFromString<CueCard>(text)
        } catch (e: Exception) {
            return CardDecodeResult.Malformed(e.message ?: "This does not look like a Cues card.")
        }
        return if (digestOf(card.copy(digest = "")) == card.digest) {
            CardDecodeResult.Ok(card)
        } else {
            CardDecodeResult.Tampered
        }
    }

    /**
     * Turns a decoded card into a routine this phone can review — never one
     * it can arm directly. Every device, place and context reference is
     * re-resolved by label against [pairedDevices]/[contexts]/[places];
     * a reference this phone cannot resolve refuses the whole import rather
     * than importing a partial or guessed-at routine.
     */
    fun reimport(
        text: String,
        pairedDevices: List<PairedDevice>,
        contexts: List<NamedContext>,
        places: List<Place>,
        idGenerator: () -> String = { "routine-" + java.util.UUID.randomUUID() },
    ): ReimportResult {
        val card = when (val decoded = decode(text)) {
            is CardDecodeResult.Ok -> decoded.card
            CardDecodeResult.Tampered -> return ReimportResult.Tampered
            is CardDecodeResult.Malformed -> return ReimportResult.Malformed(decoded.reason)
        }
        if (card.cardSchemaVersion != CURRENT_CARD_SCHEMA) return ReimportResult.UnsupportedSchema(card.cardSchemaVersion)
        if (card.schemaVersion != SCHEMA_VERSION) {
            return ReimportResult.Malformed(
                "This cue was written for schema ${card.schemaVersion}; this build speaks $SCHEMA_VERSION.",
            )
        }

        val trigger = when (val result = resolveTrigger(card.trigger, pairedDevices, places)) {
            is Resolved.Ok -> result.value
            is Resolved.Missing -> return ReimportResult.MissingEntity(result.kind, result.label)
        }
        val conditions = card.conditions.map { condition ->
            when (val result = resolveCondition(condition, pairedDevices, contexts, places)) {
                is Resolved.Ok -> result.value
                is Resolved.Missing -> return ReimportResult.MissingEntity(result.kind, result.label)
            }
        }

        val routine = Normalizer.normalize(
            Routine(
                id = idGenerator(),
                version = 1,
                schemaVersion = card.schemaVersion,
                sourceText = card.sourceText,
                title = card.title,
                trigger = trigger,
                conditions = conditions,
                actions = card.actions,
                endConditions = card.endConditions,
                cleanupPolicy = card.cleanupPolicy,
                rearmPolicy = card.rearmPolicy,
                // Overwritten by normalize() from the registry; never taken from the card.
                requiredCapabilities = emptySet(),
                status = RoutineStatus.DRAFT,
                draftedBy = DraftSourceId.IMPORTED_CARD,
            ),
        )
        return ReimportResult.Ready(routine)
    }

    fun digestOf(card: CueCard): String {
        val canonical = json.encodeToString(card.copy(digest = ""))
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** One field's resolution against this phone's own stores — [kind]/[label] name exactly what was missing, if anything was. */
    private sealed interface Resolved<out T> {
        data class Ok<out T>(val value: T) : Resolved<T>
        data class Missing(val kind: String, val label: String) : Resolved<Nothing>
    }

    private fun resolveTrigger(trigger: Trigger, devices: List<PairedDevice>, places: List<Place>): Resolved<Trigger> = when (trigger) {
        is Trigger.BluetoothConnection -> devices.firstOrNull { it.label.equals(trigger.deviceLabel, ignoreCase = true) }
            ?.let { Resolved.Ok(trigger.copy(deviceId = it.id, deviceLabel = it.label)) }
            ?: Resolved.Missing("paired device", trigger.deviceLabel)

        is Trigger.PlaceTransition -> places.firstOrNull { it.label.equals(trigger.label, ignoreCase = true) }
            ?.let { Resolved.Ok(trigger.copy(placeId = it.id, placeVersion = it.version, label = it.label)) }
            ?: Resolved.Missing("place", trigger.label)

        else -> Resolved.Ok(trigger)
    }

    private fun resolveCondition(
        condition: Condition,
        devices: List<PairedDevice>,
        contexts: List<NamedContext>,
        places: List<Place>,
    ): Resolved<Condition> = when (condition) {
        is Condition.DeviceConnected -> devices.firstOrNull { it.label.equals(condition.deviceLabel, ignoreCase = true) }
            ?.let { Resolved.Ok(condition.copy(deviceId = it.id, deviceLabel = it.label)) }
            ?: Resolved.Missing("paired device", condition.deviceLabel)

        is Condition.InContext -> contexts.firstOrNull { it.label.equals(condition.label, ignoreCase = true) }
            ?.let { Resolved.Ok(condition.copy(contextId = it.id, contextVersion = it.version, label = it.label)) }
            ?: Resolved.Missing("named context", condition.label)

        is Condition.AtPlace -> places.firstOrNull { it.label.equals(condition.label, ignoreCase = true) }
            ?.let { Resolved.Ok(condition.copy(placeId = it.id, placeVersion = it.version, label = it.label)) }
            ?: Resolved.Missing("place", condition.label)

        else -> Resolved.Ok(condition)
    }
}
