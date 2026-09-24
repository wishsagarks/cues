package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.eval.describe
import com.cues.core.model.Capability
import com.cues.core.model.Condition
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextValue
import kotlin.reflect.KClass

/**
 * Shared evaluation for both calendar conditions — only the polarity of
 * "matches" differs between [Condition.CalendarBusy] and
 * [Condition.CalendarNotBusy], the same shape [BatteryThresholdKit] already
 * gives the two battery-threshold conditions.
 *
 * `READ_CALENDAR` missing (or never granted) is not read as "not busy": a
 * calendar app that hasn't answered is an [ContextValue.Unknown] read, so a
 * routine gated on being free during a meeting cannot fire from a permission
 * gap it happened to have.
 */
abstract class CalendarConditionKit<C : Condition> : ConditionKit<C> {
    abstract fun matches(busy: Boolean): Boolean
    abstract fun describe(): String

    override fun evaluate(condition: C, snapshot: ContextSnapshot, freshness: FreshnessPolicy): Reason =
        when (val busy = snapshot.calendarBusy) {
            is ContextValue.Unknown ->
                Reason(ReasonCode.CALENDAR_UNKNOWN, Truth.UNKNOWN, "The calendar could not be read (${busy.reason.describe()}).")
            is ContextValue.Known ->
                if (matches(busy.value)) {
                    Reason(ReasonCode.CALENDAR_AS_REQUIRED, Truth.MATCH, "The calendar is ${if (busy.value) "busy" else "free"}, as required.")
                } else {
                    Reason(ReasonCode.CALENDAR_NOT_AS_REQUIRED, Truth.NO_MATCH, "The calendar is ${if (busy.value) "busy" else "free"}, not ${describe()}.")
                }
        }

    override fun validate(condition: C): List<Finding> = emptyList()
    override fun capabilities(condition: C): Set<Capability> = setOf(Capability.READ_CALENDAR)
    override fun describe(condition: C): String = describe()
}

object CalendarBusyKit : CalendarConditionKit<Condition.CalendarBusy>() {
    override val type: KClass<Condition.CalendarBusy> = Condition.CalendarBusy::class
    override fun matches(busy: Boolean) = busy
    override fun describe() = "the calendar busy"
    override fun semanticForm(condition: Condition.CalendarBusy) = "calendarBusy"
}

object CalendarNotBusyKit : CalendarConditionKit<Condition.CalendarNotBusy>() {
    override val type: KClass<Condition.CalendarNotBusy> = Condition.CalendarNotBusy::class
    override fun matches(busy: Boolean) = !busy
    override fun describe() = "the calendar free"
    override fun semanticForm(condition: Condition.CalendarNotBusy) = "calendarNotBusy"
}
