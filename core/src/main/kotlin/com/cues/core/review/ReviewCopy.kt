package com.cues.core.review

import com.cues.core.model.*
import com.cues.core.receipt.friendly

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

    fun whenText(routine: Routine): String = when (val t = routine.trigger) {
        is Trigger.BluetoothConnection -> when (t.transition) {
            DeviceTransition.CONNECTED -> "${t.deviceLabel} connects"
            DeviceTransition.DISCONNECTED -> "${t.deviceLabel} disconnects"
        }

        is Trigger.Charging -> when (t.transition) {
            PowerTransition.PLUGGED_IN -> "the charger is plugged in"
            PowerTransition.UNPLUGGED -> "the charger is unplugged"
        }

        Trigger.Manual -> "you run it by hand"
    }

    fun ifText(routine: Routine): String {
        if (routine.conditions.isEmpty()) return "always"
        return routine.conditions.joinToString(", ") { condition ->
            when (condition) {
                is Condition.DaysOfWeek -> condition.days.describeDays()
                // The normalization is shown, never hidden: "after 6 PM" became a
                // window running to midnight, and the user gets to disagree.
                is Condition.TimeWindow -> if (condition.endExclusive.minutesOfDay == 0) {
                    "at or after ${condition.startInclusive} local time"
                } else {
                    "${condition.startInclusive} to ${condition.endExclusive} local time"
                }

                is Condition.ChargingState ->
                    if (condition.charging) "the phone is charging" else "the phone is not charging"
            }
        }
    }

    fun doText(routine: Routine): String = routine.actions.joinToString(", ") { spec ->
        when (val args = spec.args) {
            is ActionArgs.FocusTimer -> "start a ${args.durationMinutes}-minute focus timer"
            is ActionArgs.Dnd -> "request our quiet-notifications rule"
            is ActionArgs.Notify -> "show \"${args.message}\""
            ActionArgs.None -> spec.actionId.friendly().lowercase()
        }
    }

    fun untilText(routine: Routine): String = routine.endConditions.joinToString(", ") { end ->
        when (end) {
            is EndCondition.Duration -> "${end.minutes} minutes have passed"
            EndCondition.TriggerReversed -> "${triggerNounFor(routine)} goes away"
            EndCondition.ManualStop -> "you stop it"
        }
    }

    fun restoreText(routine: Routine): String {
        val owned = routine.actions.mapNotNull { spec ->
            when (spec.actionId) {
                ActionId.START_FOCUS_TIMER -> "end our timer"
                ActionId.REQUEST_DND -> "release our quiet rule"
                ActionId.NOTIFY_RESULT -> null
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

    fun triggerNounFor(routine: Routine): String = when (val t = routine.trigger) {
        is Trigger.BluetoothConnection -> t.deviceLabel
        is Trigger.Charging -> "the charger"
        Trigger.Manual -> "the run"
    }

    private fun Set<Day>.describeDays(): String = when (this) {
        WEEKDAYS -> "Monday to Friday"
        WEEKEND -> "Saturday and Sunday"
        else -> Day.entries.filter { it in this }
            .joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
    }

    fun Capability.friendlyName(): String = when (this) {
        Capability.BLUETOOTH_CONNECT -> "Bluetooth"
        Capability.NOTIFICATION_POLICY_ACCESS -> "Do Not Disturb access"
        Capability.POST_NOTIFICATIONS -> "notifications"
        Capability.EXACT_ALARM -> "exact alarms"
        Capability.BATTERY_STATE -> "battery state"
    }
}
