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

    fun ifText(routine: Routine): String {
        if (routine.conditions.isEmpty()) return "always"
        return routine.conditions.joinToString(", ") { SignalRegistry.reviewText(it) }
    }

    fun doText(routine: Routine): String = routine.actions.joinToString(", ") { spec ->
        when (val args = spec.args) {
            is ActionArgs.FocusTimer -> "start a ${args.durationMinutes}-minute focus timer"
            is ActionArgs.Dnd -> "request our quiet-notifications rule"
            is ActionArgs.Notify -> "show \"${args.message}\""
            is ActionArgs.PinnedNote -> "keep \"${args.message}\" pinned"
            ActionArgs.None -> spec.actionId.friendly().lowercase()
        }
    }

    fun untilText(routine: Routine): String = routine.endConditions.joinToString(", ") { end ->
        if (end == EndCondition.TriggerReversed) "${triggerNounFor(routine)} ${SignalRegistry.reviewText(end)}"
        else SignalRegistry.reviewText(end)
    }

    fun restoreText(routine: Routine): String {
        val owned = routine.actions.mapNotNull { spec ->
            when (spec.actionId) {
                ActionId.START_FOCUS_TIMER -> "end our timer"
                ActionId.REQUEST_DND -> "release our quiet rule"
                ActionId.NOTIFY_RESULT -> null
                ActionId.PINNED_NOTE -> "remove our pinned note"
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
    }

    fun ActionRisk.friendlyName(): String = when (this) {
        ActionRisk.OWNED_AND_REVERSIBLE -> "Owned & reversible"
        ActionRisk.LOCAL_NOTICE -> "Local notice"
    }
}
