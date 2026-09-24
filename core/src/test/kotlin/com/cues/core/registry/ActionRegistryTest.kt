package com.cues.core.registry

import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionSpec
import com.cues.core.model.Capability
import com.cues.core.model.MediaCommand
import com.cues.core.model.OwnedResource
import com.cues.core.model.RingerModeKind
import com.cues.core.model.UtilityId
import com.cues.core.model.UtilityState
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ActionRegistryTest {

    @Test
    fun `every action id has a definition with a declared risk and presence`() {
        ActionId.entries.forEach { id ->
            val definition = assertIs<ActionDefinition>(
                ActionRegistry.definition(id) ?: error("$id has no registry entry"),
            )
            assertEquals(id, definition.id)
        }
    }

    @Test
    fun `handoff and external-unowned actions never require the user be absent to matter`() {
        val handoffIds = setOf(
            ActionId.OPEN_APP, ActionId.COMPOSE_MESSAGE, ActionId.ADD_CALENDAR_EVENT, ActionId.OPEN_LINK,
        )
        handoffIds.forEach { id ->
            val definition = ActionRegistry.definition(id)!!
            assertEquals(ActionRisk.HANDOFF, definition.risk, "$id should be a handoff")
            assertEquals(Presence.NEEDS_USER, definition.presence, "$id should need the user present")
        }

        setOf(ActionId.SET_ALARM, ActionId.MEDIA_CONTROL).forEach { id ->
            val definition = ActionRegistry.definition(id)!!
            assertEquals(ActionRisk.EXTERNAL_UNOWNED, definition.risk, "$id should be external-unowned")
        }
        // MEDIA_CONTROL can run headless (a media-key dispatch needs no UI);
        // SET_ALARM shows the clock app's own confirm screen without a
        // special-purpose permission this app does not otherwise request.
        assertEquals(Presence.UNATTENDED_OK, ActionRegistry.definition(ActionId.MEDIA_CONTROL)!!.presence)
        assertEquals(Presence.NEEDS_USER, ActionRegistry.definition(ActionId.SET_ALARM)!!.presence)
    }

    @Test
    fun `ringer mode is owned, reversible and derives its own capability`() {
        val definition = ActionRegistry.definition(ActionId.RINGER_MODE)!!
        assertEquals(ActionRisk.OWNED_AND_REVERSIBLE, definition.risk)
        assertEquals(OwnedResource.RINGER_MODE, definition.owns)

        val owned = ActionRegistry.ownedResourcesFor(
            listOf(ActionSpec(ActionId.RINGER_MODE, ActionArgs.RingerMode(RingerModeKind.SILENT))),
        )
        assertEquals(setOf(OwnedResource.RINGER_MODE), owned)
    }

    @Test
    fun `a pre-filled message over the character limit is rejected`() {
        val definition = ActionRegistry.definition(ActionId.COMPOSE_MESSAGE)!!
        val tooLong = ActionArgs.ComposeMessage(text = "x".repeat(ActionRegistry.MAX_MESSAGE_CHARS + 1))

        assertIs<ArgResult.Invalid>(definition.validate(tooLong))
        assertIs<ArgResult.Valid>(definition.validate(ActionArgs.ComposeMessage(text = "on my way")))
    }

    @Test
    fun `a link must use an allowed scheme — never a scheme a draft merely asserts`() {
        val definition = ActionRegistry.definition(ActionId.OPEN_LINK)!!

        assertIs<ArgResult.Valid>(definition.validate(ActionArgs.OpenLink("https://example.com")))
        assertIs<ArgResult.Valid>(definition.validate(ActionArgs.OpenLink("tel:+911234567890")))
        assertIs<ArgResult.Invalid>(definition.validate(ActionArgs.OpenLink("javascript:alert(1)")))
        assertIs<ArgResult.Invalid>(definition.validate(ActionArgs.OpenLink("intent://evil")))
    }

    @Test
    fun `an alarm outside 24-hour bounds is rejected`() {
        val definition = ActionRegistry.definition(ActionId.SET_ALARM)!!

        assertIs<ArgResult.Valid>(definition.validate(ActionArgs.Alarm(hour = 7, minute = 30)))
        assertIs<ArgResult.Invalid>(definition.validate(ActionArgs.Alarm(hour = 24, minute = 0)))
        assertIs<ArgResult.Invalid>(definition.validate(ActionArgs.Alarm(hour = 7, minute = 60)))
    }

    @Test
    fun `a calendar event needs a title and a bounded duration`() {
        val definition = ActionRegistry.definition(ActionId.ADD_CALENDAR_EVENT)!!

        assertIs<ArgResult.Invalid>(
            definition.validate(ActionArgs.CalendarEvent(title = "", durationMinutes = 30)),
        )
        assertIs<ArgResult.Invalid>(
            definition.validate(ActionArgs.CalendarEvent(title = "Exam", durationMinutes = 0)),
        )
        assertIs<ArgResult.Valid>(
            definition.validate(ActionArgs.CalendarEvent(title = "Exam", durationMinutes = 60)),
        )
    }

    @Test
    fun `opening an app needs a chosen package and label, never an empty placeholder`() {
        val definition = ActionRegistry.definition(ActionId.OPEN_APP)!!

        assertIs<ArgResult.Invalid>(definition.validate(ActionArgs.OpenApp(packageName = "", label = "Spotify")))
        assertIs<ArgResult.Invalid>(definition.validate(ActionArgs.OpenApp(packageName = "com.spotify.music", label = "")))
        assertIs<ArgResult.Valid>(
            definition.validate(ActionArgs.OpenApp(packageName = "com.spotify.music", label = "Spotify")),
        )
    }

    @Test
    fun `media control accepts any of the closed commands`() {
        val definition = ActionRegistry.definition(ActionId.MEDIA_CONTROL)!!
        MediaCommand.entries.forEach {
            assertTrue(definition.validate(ActionArgs.MediaControl(it)) is ArgResult.Valid)
        }
    }

    @Test
    fun `use utility is the highest risk class, needs the user present, and derives the accessibility capability`() {
        val definition = ActionRegistry.definition(ActionId.USE_UTILITY)!!
        assertEquals(ActionRisk.UI_AUTOMATION, definition.risk)
        assertEquals(Presence.NEEDS_USER, definition.presence)
        assertEquals(OwnedResource.UTILITY_CONTRIBUTION, definition.owns)
        assertEquals(setOf(Capability.ACCESSIBILITY_SERVICE), definition.requiredCapabilities)

        assertIs<ArgResult.Valid>(definition.validate(ActionArgs.UseUtility(UtilityId.EYE_PROTECTION, UtilityState.ON)))
        assertIs<ArgResult.Invalid>(definition.validate(ActionArgs.None))

        val owned = ActionRegistry.ownedResourcesFor(
            listOf(ActionSpec(ActionId.USE_UTILITY, ActionArgs.UseUtility(UtilityId.EYE_PROTECTION, UtilityState.ON))),
        )
        assertEquals(setOf(OwnedResource.UTILITY_CONTRIBUTION), owned)
    }
}
