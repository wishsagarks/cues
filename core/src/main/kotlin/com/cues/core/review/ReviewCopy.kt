package com.cues.core.review

import com.cues.core.model.*
import com.cues.core.registry.ActionRisk
import com.cues.core.receipt.friendly
import com.cues.core.signals.SignalRegistry

/**
 * Renders the WHEN / IF / DO / UNTIL / RESTORE review — and its surrounding
 * repeat-behaviour and access copy — from a routine's normalized fields.
 *
 * One implementation, used by the CLI (`./dev d`) and the Android Review
 * screen alike, so a phrasing decision made here is a phrasing decision made
 * everywhere. Duplicating this per surface is exactly how a CLI and an app
 * end up disagreeing about what a cue does.
 */
object ReviewCopy {

    data class PermissionCheckCopy(
        val purpose: String,
        val frequency: String,
    )

    fun whenText(routine: Routine): String = SignalRegistry.reviewText(routine.trigger)

    fun ifText(routine: Routine): String = conditionLines(routine).joinToString(", ")

    fun doText(routine: Routine): String = actionLines(routine).joinToString(", ")

    fun untilText(routine: Routine): String = endLines(routine).joinToString(", ")

    // One line per item. The joined *Text functions above are these lines
    // joined, never a second rendering, so a card that shows rows and a
    // sentence that shows the same cue cannot word it two different ways.

    /** One line per condition; a single "always" when there are none, so the IF row is never blank. */
    fun conditionLines(routine: Routine): List<String> =
        if (routine.conditions.isEmpty()) listOf("always")
        else routine.conditions.map { SignalRegistry.reviewText(it) }

    /** One line per action, in the routine's (normalized) order. */
    fun actionLines(routine: Routine): List<String> = routine.actions.map { actionLine(it) }

    /** One line per end condition; the trigger-reversed ending names the trigger it reverses. */
    fun endLines(routine: Routine): List<String> = routine.endConditions.map { end ->
        if (end == EndCondition.TriggerReversed) "${triggerNounFor(routine)} ${SignalRegistry.reviewText(end)}"
        else SignalRegistry.reviewText(end)
    }

    private fun actionLine(spec: ActionSpec): String =
        when (val args = spec.args) {
            is ActionArgs.FocusTimer -> "start a ${args.durationMinutes}-minute focus timer"
            is ActionArgs.Dnd -> "request our quiet-notifications rule"
            is ActionArgs.Notify -> "show \"${args.message}\""
            is ActionArgs.PinnedNote -> "keep \"${args.message}\" pinned"
            is ActionArgs.OpenApp -> "open ${args.label} — you finish this"
            is ActionArgs.ComposeMessage -> "pre-fill a message: \"${args.text}\" — you send it"
            is ActionArgs.ComposeWhatsApp -> "open WhatsApp with \"${args.text}\" for ${args.contactHint ?: "your chosen chat"} — you send it"
            is ActionArgs.ComposeEmail -> "draft an email${args.subject?.let { " (\"$it\")" } ?: ""} to " +
                "${args.contactHint ?: "an address you choose"}: \"${args.text}\" — you send it"
            is ActionArgs.CalendarEvent -> "add a calendar event: \"${args.title}\" — you save it"
            is ActionArgs.Alarm -> "ask the clock app to set an alarm for %02d:%02d".format(args.hour, args.minute)
            is ActionArgs.MediaControl -> "send a ${args.command.name.lowercase()} media command"
            is ActionArgs.RingerMode -> "set the ringer to ${args.mode.name.lowercase()}"
            is ActionArgs.OpenLink -> "open ${args.url} — you finish this"
            is ActionArgs.UseUtility -> "turn ${args.utilityId.name.lowercase().replace('_', ' ')} ${args.state.name.lowercase()}"
            is ActionArgs.SimulatedSend -> "simulate sending \"${args.message}\" to " +
                "${args.recipientHint ?: "the recipient you name"} via " +
                args.channels.sortedBy { it.name }.joinToString(" and ") { it.reviewLabel() } +
                " — simulated only, nothing is actually sent"
            is ActionArgs.MailDigest -> "post a mail digest (${args.deliveryMode.reviewLabel()})"
            ActionArgs.None -> spec.actionId.friendly().lowercase()
        }

    private fun SendChannel.reviewLabel(): String = when (this) {
        SendChannel.NOTIFICATION_BAR -> "the notification bar"
        SendChannel.WHATSAPP -> "WhatsApp"
        SendChannel.SMS -> "SMS"
        SendChannel.EMAIL -> "email"
    }

    private fun DigestDeliveryMode.reviewLabel(): String = when (this) {
        DigestDeliveryMode.MCQ_VOICE_WHATSAPP -> "a simulated voice-note send to WhatsApp"
        DigestDeliveryMode.SUMMARY_NEEDS_INPUT -> "a summary that needs your input"
        DigestDeliveryMode.GEMMA_PARSABLE -> "Gemma's approved parsable format"
    }

    fun restoreText(routine: Routine): String {
        val owned = routine.actions.mapNotNull { spec ->
            when (spec.actionId) {
                ActionId.START_FOCUS_TIMER -> "end our timer"
                ActionId.REQUEST_DND -> "release our quiet rule"
                ActionId.NOTIFY_RESULT -> null
                ActionId.PINNED_NOTE -> "remove our pinned note"
                ActionId.RINGER_MODE -> "restore the ringer, if nothing else has changed it since"
                ActionId.USE_UTILITY -> "restore the utility to what it was, if nothing else has changed it since"
                ActionId.OPEN_APP,
                ActionId.COMPOSE_MESSAGE,
                ActionId.COMPOSE_WHATSAPP,
                ActionId.COMPOSE_EMAIL,
                ActionId.ADD_CALENDAR_EVENT,
                ActionId.SET_ALARM,
                ActionId.MEDIA_CONTROL,
                ActionId.OPEN_LINK,
                ActionId.SIMULATE_SEND,
                ActionId.MAIL_DIGEST,
                -> null
            }
        }
        if (owned.isEmpty()) return "nothing to release"
        // The caveat is part of the promise, not a footnote.
        return owned.joinToString(", ") + " — and nothing else. Another quiet mode stays as it is."
    }

    fun repeatText(routine: Routine): String = buildString {
        append("one session at a time")
        if (routine.rearmPolicy.reconnectGraceSeconds > 0) {
            append("; a dropout shorter than ${routine.rearmPolicy.reconnectGraceSeconds}s does not end it")
        }
        if (routine.rearmPolicy.cooldownSeconds > 0) {
            append("; waits ${routine.rearmPolicy.cooldownSeconds}s between runs")
        }
        if (routine.rearmPolicy.oncePerLocalDay) {
            append("; at most once per local day")
        }
    }

    fun accessText(routine: Routine): String =
        routine.requiredCapabilities.joinToString(", ") { it.friendlyName() }.ifEmpty { "none" }

    fun triggerNounFor(routine: Routine): String = SignalRegistry.noun(routine.trigger)

    fun Capability.friendlyName(): String = when (this) {
        Capability.BLUETOOTH_CONNECT -> "Bluetooth"
        Capability.NOTIFICATION_POLICY_ACCESS -> "Do Not Disturb access"
        Capability.POST_NOTIFICATIONS -> "notifications"
        Capability.EXACT_ALARM -> "exact alarms"
        Capability.BATTERY_STATE -> "battery state"
        Capability.NETWORK_STATE -> "network state"
        Capability.LOCATION_FOR_WIFI_NAME -> "location for a Wi-Fi name"
        Capability.LOCATION_FOREGROUND -> "foreground location"
        Capability.LOCATION_BACKGROUND -> "background location"
        Capability.ACCESSIBILITY_SERVICE -> "the Cues utility-bindings accessibility service"
        Capability.READ_CALENDAR -> "calendar read access"
    }

    /** Explains the actual, bounded use of each requested capability before approval. */
    fun Capability.permissionCheckCopy(): PermissionCheckCopy = when (this) {
        Capability.BLUETOOTH_CONNECT -> PermissionCheckCopy(
            purpose = "Identify the paired device named in this cue when Android delivers its connection change.",
            frequency = "Used only for that cue's Bluetooth connection or disconnection events; Cues does not scan nearby devices.",
        )

        Capability.NOTIFICATION_POLICY_ACCESS -> PermissionCheckCopy(
            purpose = "Create and release Cues' own quiet-notifications contribution.",
            frequency = "Used when a matching session starts and when it ends; it never resets another app's quiet mode.",
        )

        Capability.POST_NOTIFICATIONS -> PermissionCheckCopy(
            purpose = "Show the session timer and truthful result or cleanup notices.",
            frequency = "Used only when a session starts, ends or needs attention.",
        )

        Capability.EXACT_ALARM -> PermissionCheckCopy(
            purpose = "Schedule this cue's approved timer deadline.",
            frequency = "One deadline per active timer; it is cleared when that session ends.",
        )

        Capability.BATTERY_STATE -> PermissionCheckCopy(
            purpose = "Read the current charging state when an approved power event arrives.",
            frequency = "Read only at charging events or an approved charging check; Cues does not infer charging history.",
        )

        Capability.NETWORK_STATE -> PermissionCheckCopy(
            purpose = "Observe whether Wi-Fi connects or disconnects for this cue.",
            frequency = "Used only for the approved Wi-Fi signals; Cues does not inspect unrelated traffic.",
        )

        Capability.LOCATION_FOR_WIFI_NAME -> PermissionCheckCopy(
            purpose = "Resolve a named Wi-Fi network when the operating system permits it.",
            frequency = "Not currently armable until the named-network device spike is complete.",
        )

        Capability.LOCATION_FOREGROUND -> PermissionCheckCopy(
            purpose = "Read one location after you save a declared place, or check that declared place.",
            frequency = "Only for places you explicitly save; Cues never guesses Home or another place.",
        )

        Capability.LOCATION_BACKGROUND -> PermissionCheckCopy(
            purpose = "Receive an arrival or exit for a place-trigger cue you approved.",
            frequency = "Only while an armed cue uses that declared place transition.",
        )

        Capability.ACCESSIBILITY_SERVICE -> PermissionCheckCopy(
            purpose = "Replay a macro you taught, only against the exact toggle it was taught on.",
            frequency = "Used only when a USE_UTILITY action in this cue fires, and only while you are present.",
        )

        Capability.READ_CALENDAR -> PermissionCheckCopy(
            purpose = "Read whether the calendar shows a busy event right now, for a calendar-busy or calendar-free condition.",
            frequency = "Read only when this cue's trigger fires; Cues does not read event titles, attendees or any other detail.",
        )
    }

    fun ActionRisk.friendlyName(): String = when (this) {
        ActionRisk.OWNED_AND_REVERSIBLE -> "Owned & reversible"
        ActionRisk.LOCAL_NOTICE -> "Local notice"
        ActionRisk.HANDOFF -> "Handoff — you finish this"
        ActionRisk.EXTERNAL_UNOWNED -> "Cues cannot undo this"
        ActionRisk.UI_AUTOMATION -> "Automates another app's screen"
    }

    // ---------------------------------------------------------------- labels
    //
    // The word a screen shows for each state enum. Kept here, next to every
    // other piece of review copy, so no raw enum name (EXIT_PENDING,
    // COMPENSATION_FAILED) ever has to reach a person, and a chip on Now and a
    // row in Receipts cannot call the same state two different things.
    // Every `when` is exhaustive, so a new enum member will not compile until
    // someone decides what it is called.

    fun RoutineStatus.friendlyName(): String = when (this) {
        RoutineStatus.DRAFT -> "Draft"
        RoutineStatus.INVALID -> "Needs changes"
        RoutineStatus.REVIEWABLE -> "Ready to review"
        RoutineStatus.ARMED -> "Armed"
        RoutineStatus.PAUSED -> "Paused"
        RoutineStatus.DISABLED -> "Off"
    }

    fun SessionState.friendlyName(): String = when (this) {
        SessionState.STARTING -> "Starting"
        SessionState.ACTIVE -> "Running"
        // Still running in effect: the grace window has not run out.
        SessionState.EXIT_PENDING -> "Ending unless it reconnects"
        SessionState.ENDING -> "Ending"
        SessionState.COMPLETED -> "Finished"
        SessionState.CANCELLED -> "Cancelled"
        // Never "Running": not everything this session was meant to do happened.
        SessionState.PARTIAL -> "Running, not everything started"
        SessionState.CLEANUP_PENDING -> "Cleanup outstanding"
        SessionState.FAILED -> "Failed"
    }

    fun ActionState.friendlyName(): String = when (this) {
        ActionState.NOT_STARTED -> "Not started"
        ActionState.IN_PROGRESS -> "In progress"
        ActionState.SUCCEEDED -> "Done"
        ActionState.BLOCKED -> "Blocked"
        ActionState.FAILED -> "Failed"
        ActionState.COMPENSATED -> "Undone"
        ActionState.COMPENSATION_FAILED -> "Couldn't undo"
        ActionState.PENDING -> "Waiting for you"
    }

    fun EndReason.friendlyName(): String = when (this) {
        EndReason.DEADLINE_REACHED -> "Timer finished"
        EndReason.TRIGGER_REVERSED -> "Trigger went away"
        EndReason.MANUAL_STOP -> "You stopped it"
        EndReason.ROUTINE_PAUSED -> "Cue paused"
        EndReason.RECONCILED_EXPIRED -> "Finished while Cues wasn't running"
        EndReason.START_FAILED -> "Couldn't start"
        // Not "disconnected": no disconnect was ever observed.
        EndReason.COVERAGE_GAP -> "Coverage gap"
    }

    fun Verification.friendlyName(): String = when (this) {
        Verification.READ_BACK -> "Checked"
        // The amber "assumed" state: steps landed, the setting was never read.
        Verification.STEPS_CONFIRMED -> "Assumed"
        Verification.NONE -> "Not checked"
        // Never "Done": nothing external happened, by design — see ActionId.SIMULATE_SEND.
        Verification.SIMULATED -> "Simulated — nothing sent"
    }
}
