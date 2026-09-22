package com.cues.app

import com.cues.core.model.*
import com.cues.core.registry.ActionRegistry

/**
 * A stand-in cue for the scaffold screen, so the UI has something real to
 * render before authoring exists.
 *
 * It is the routine from the PRS review example. Delete this once the Review
 * surface can show a drafted cue; it is listed as CL-02 in CLEANUP.md.
 */
object SampleRoutine {

    fun focusOnEarbuds(): Routine {
        val actions = listOf(
            ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45)),
            ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()),
        )

        return Routine(
            id = "sample",
            version = 1,
            sourceText = "When my earbuds connect after 6 PM on weekdays, start a 45-minute " +
                "focus timer and quiet notifications. End it if I disconnect.",
            title = "Focus when earbuds connect",
            trigger = Trigger.BluetoothConnection("AA:BB:CC:DD:EE:FF", "TWS Air Pro", DeviceTransition.CONNECTED),
            conditions = listOf(
                Condition.DaysOfWeek(WEEKDAYS),
                Condition.TimeWindow(LocalTimeOfDay(18, 0), LocalTimeOfDay(0, 0)),
            ),
            actions = actions,
            endConditions = listOf(
                EndCondition.TriggerReversed,
                EndCondition.Duration(45),
                EndCondition.ManualStop,
            ),
            cleanupPolicy = CleanupPolicy(),
            rearmPolicy = RearmPolicy(),
            requiredCapabilities = ActionRegistry.capabilitiesFor(actions),
            status = RoutineStatus.DRAFT,
        )
    }
}
