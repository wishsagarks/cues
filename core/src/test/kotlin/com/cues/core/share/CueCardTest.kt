package com.cues.core.share

import com.cues.core.Fixtures
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.Capability
import com.cues.core.model.Condition
import com.cues.core.model.Day
import com.cues.core.model.DraftSourceId
import com.cues.core.model.NamedContext
import com.cues.core.model.Place
import com.cues.core.model.RoutineStatus
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CueCardTest {

    private val senderDevice = PairedDevice(Fixtures.EARBUDS_ID, Fixtures.EARBUDS_LABEL, setOf("earbuds"))

    @Test
    fun `a card round-trips into a fresh, unapproved draft with capabilities re-derived, not trusted`() {
        val routine = Fixtures.heroRoutine()
        val card = CueCards.from(routine)
        val encoded = CueCards.encode(card)

        val result = CueCards.reimport(
            encoded,
            pairedDevices = listOf(senderDevice),
            contexts = emptyList(),
            places = emptyList(),
        )

        val imported = assertIs<ReimportResult.Ready>(result).routine
        assertEquals(RoutineStatus.DRAFT, imported.status)
        assertEquals(null, imported.approvedDigest)
        assertEquals(DraftSourceId.IMPORTED_CARD, imported.draftedBy)
        assertNotEquals(routine.id, imported.id, "an imported card never reuses another phone's routine id")
        // Recomputed locally by the same normalizer every other draft goes
        // through, never copied from the card, which carries no capabilities
        // field at all.
        assertEquals(
            com.cues.core.compile.Normalizer.normalize(routine).requiredCapabilities,
            imported.requiredCapabilities,
        )
    }

    @Test
    fun `an exported card carries no approval, status or capability claim`() {
        val approved = Fixtures.heroRoutine().copy(
            approvedDigest = "something-real",
            status = RoutineStatus.ARMED,
        )

        val encoded = CueCards.encode(CueCards.from(approved))

        assertTrue("approvedDigest" !in encoded)
        assertTrue("\"ARMED\"" !in encoded)
        assertTrue("requiredCapabilities" !in encoded)
    }

    @Test
    fun `a device the receiving phone does not have refuses the import outright`() {
        val routine = Fixtures.heroRoutine()
        val encoded = CueCards.encode(CueCards.from(routine))

        val result = CueCards.reimport(encoded, pairedDevices = emptyList(), contexts = emptyList(), places = emptyList())

        val missing = assertIs<ReimportResult.MissingEntity>(result)
        assertEquals(Fixtures.EARBUDS_LABEL, missing.label)
    }

    @Test
    fun `a device with the same label but a different address on the receiving phone is what gets bound, never the sender's id`() {
        val routine = Fixtures.heroRoutine()
        val encoded = CueCards.encode(CueCards.from(routine))
        val receiverDevice = PairedDevice("11:22:33:44:55:66", Fixtures.EARBUDS_LABEL, setOf("earbuds"))

        val result = CueCards.reimport(encoded, pairedDevices = listOf(receiverDevice), contexts = emptyList(), places = emptyList())

        val routineImported = assertIs<ReimportResult.Ready>(result).routine
        val trigger = assertIs<com.cues.core.model.Trigger.BluetoothConnection>(routineImported.trigger)
        assertEquals("11:22:33:44:55:66", trigger.deviceId)
        assertNotEquals(Fixtures.EARBUDS_ID, trigger.deviceId)
    }

    @Test
    fun `a named context is re-resolved by label against the receiving phone's own context, not the sender's id or version`() {
        val routine = Fixtures.heroRoutine(conditions = listOf(Condition.InContext("sender-ctx-1", 3, "Desk")))
        val encoded = CueCards.encode(CueCards.from(routine))
        val receiverContext = NamedContext("receiver-ctx-9", "Desk", version = 1, predicates = emptyList())

        val result = CueCards.reimport(
            encoded, pairedDevices = listOf(senderDevice), contexts = listOf(receiverContext), places = emptyList(),
        )

        val imported = assertIs<ReimportResult.Ready>(result).routine
        val condition = assertIs<Condition.InContext>(imported.conditions.single())
        assertEquals("receiver-ctx-9", condition.contextId)
        assertEquals(1, condition.contextVersion)
    }

    @Test
    fun `a tampered card is refused before any resolution is attempted`() {
        val encoded = CueCards.encode(CueCards.from(Fixtures.heroRoutine()))
        val tampered = encoded.replace("\"title\":\"Focus when earbuds connect\"", "\"title\":\"Something else entirely\"")

        val result = CueCards.reimport(tampered, pairedDevices = listOf(senderDevice), contexts = emptyList(), places = emptyList())

        assertIs<ReimportResult.Tampered>(result)
    }

    @Test
    fun `garbage input is reported as malformed, never as a crash or a partial import`() {
        val result = CueCards.reimport("not json at all", pairedDevices = emptyList(), contexts = emptyList(), places = emptyList())

        assertIs<ReimportResult.Malformed>(result)
    }

    @Test
    fun `a card from a newer card schema is refused rather than guessed at`() {
        val card = CueCards.from(Fixtures.heroRoutine()).copy(cardSchemaVersion = CueCards.CURRENT_CARD_SCHEMA + 1)
        // The schema bump changes content, so re-sign the digest the way a
        // genuinely newer sender would — otherwise this would test Tampered,
        // not UnsupportedSchema.
        val resigned = card.copy(digest = CueCards.digestOf(card.copy(digest = "")))

        val result = CueCards.reimport(
            CueCards.encode(resigned), pairedDevices = listOf(senderDevice), contexts = emptyList(), places = emptyList(),
        )

        assertIs<ReimportResult.UnsupportedSchema>(result)
    }

    @Test
    fun `two cards from the same routine encode identically, so the digest is stable, not accidental`() {
        val routine = Fixtures.heroRoutine()

        val first = CueCards.encode(CueCards.from(routine))
        val second = CueCards.encode(CueCards.from(routine))

        assertEquals(first, second)
    }
}
