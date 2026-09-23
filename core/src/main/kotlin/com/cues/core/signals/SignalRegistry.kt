package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.model.Condition
import com.cues.core.model.EndCondition
import com.cues.core.model.Routine
import com.cues.core.model.Session
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import com.cues.core.registry.ActionRegistry

/**
 * The closed vocabulary of signals.
 *
 * A kit is source code, not a runtime extension point. Keeping the lists here
 * makes adding a signal an explicit compile-time review: model output can only
 * select one of these types.
 */
object SignalRegistry {
    val triggerKits: List<TriggerKit<out Trigger>> = listOf(
        BluetoothKit,
        ChargingKit,
        ManualKit,
        WifiConnectionKit,
        AtTimeKit,
        AudioOutputKit,
        PlaceTransitionKit,
    )

    val conditionKits: List<ConditionKit<out Condition>> = listOf(
        DaysOfWeekKit,
        TimeWindowKit,
        ChargingStateKit,
        DeviceConnectedKit,
        WifiConnectedKit,
        InContextKit,
        AudioOutputActiveKit,
        BatteryBelowKit,
        BatteryAtLeastKit,
        AtPlaceKit,
    )

    val endKits: List<EndKit<out EndCondition>> = listOf(
        TriggerReversedKit,
        DurationEndKit,
        ManualStopKit,
        AtTimeEndKit,
    )

    private val triggerByType = triggerKits.associateBy { it.type }
    private val conditionByType = conditionKits.associateBy { it.type }
    private val endByType = endKits.associateBy { it.type }

    init {
        check(triggerByType.keys == setOf(
            Trigger.BluetoothConnection::class,
            Trigger.Charging::class,
            Trigger.Manual::class,
            Trigger.WifiConnection::class,
            Trigger.AtTime::class,
            Trigger.AudioOutput::class,
            Trigger.PlaceTransition::class,
        )) { "Every sealed Trigger subtype must have exactly one signal kit." }
        check(conditionByType.keys == setOf(
            Condition.DaysOfWeek::class,
            Condition.TimeWindow::class,
            Condition.ChargingState::class,
            Condition.DeviceConnected::class,
            Condition.WifiConnected::class,
            Condition.InContext::class,
            Condition.AudioOutputActive::class,
            Condition.BatteryBelow::class,
            Condition.BatteryAtLeast::class,
            Condition.AtPlace::class,
        )) { "Every sealed Condition subtype must have exactly one signal kit." }
        check(endByType.keys == setOf(
            EndCondition.TriggerReversed::class,
            EndCondition.Duration::class,
            EndCondition.ManualStop::class,
            EndCondition.AtTime::class,
        )) { "Every sealed EndCondition subtype must have exactly one signal kit." }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Trigger> triggerKit(trigger: T): TriggerKit<T> =
        (triggerByType[trigger::class] ?: error("No TriggerKit for ${trigger::class.qualifiedName}")) as TriggerKit<T>

    @Suppress("UNCHECKED_CAST")
    fun <C : Condition> conditionKit(condition: C): ConditionKit<C> =
        (conditionByType[condition::class] ?: error("No ConditionKit for ${condition::class.qualifiedName}")) as ConditionKit<C>

    @Suppress("UNCHECKED_CAST")
    fun <E : EndCondition> endKit(end: E): EndKit<E> =
        (endByType[end::class] ?: error("No EndKit for ${end::class.qualifiedName}")) as EndKit<E>

    fun match(trigger: Trigger, event: TriggerEvent): Reason = triggerKit(trigger).match(trigger, event)
    fun reverses(trigger: Trigger, event: TriggerEvent): Boolean = triggerKit(trigger).reverses(trigger, event)
    fun listensFor(trigger: Trigger, kind: com.cues.core.model.EventKind): Boolean =
        triggerKit(trigger).listensFor(trigger, kind)
    fun adapterKey(trigger: Trigger): String? = triggerKit(trigger).adapterKey
    fun corpusKey(trigger: Trigger): String = semanticForm(trigger).let { form ->
        when {
            form.startsWith("bluetooth:") && form.endsWith(":CONNECTED") -> "bluetooth-connect"
            form.startsWith("bluetooth:") -> "bluetooth-disconnect"
            form == "charging:PLUGGED_IN" -> "charging-on"
            form == "charging:UNPLUGGED" -> "charging-off"
            form.startsWith("wifi:CONNECTED") -> "wifi-connect"
            form.startsWith("wifi:") -> "wifi-disconnect"
            form.startsWith("atTime:") -> "at-time"
            else -> "manual"
        }
    }

    fun evaluate(condition: Condition, snapshot: com.cues.core.model.ContextSnapshot, freshness: FreshnessPolicy): Reason =
        conditionKit(condition).evaluate(condition, snapshot, freshness)

    fun schedule(end: EndCondition, session: Session): Long? = endKit(end).schedule(end, session)

    fun semanticForm(trigger: Trigger): String = triggerKit(trigger).semanticForm(trigger)
    fun semanticForm(condition: Condition): String = conditionKit(condition).semanticForm(condition)
    fun semanticForm(end: EndCondition): String = endKit(end).semanticForm(end)

    fun describe(trigger: Trigger): String = triggerKit(trigger).describe(trigger)
    fun reviewText(trigger: Trigger): String = triggerKit(trigger).reviewText(trigger)
    fun noun(trigger: Trigger): String = triggerKit(trigger).noun(trigger)
    fun reversalEvent(trigger: Trigger, atMillis: Long): TriggerEvent? = triggerKit(trigger).reversalEvent(trigger, atMillis)
    fun describe(condition: Condition): String = conditionKit(condition).describe(condition)
    fun reviewText(condition: Condition): String = conditionKit(condition).reviewText(condition)
    fun describe(end: EndCondition): String = endKit(end).describe(end)
    fun reviewText(end: EndCondition): String = endKit(end).reviewText(end)

    fun validateTrigger(trigger: Trigger): List<Finding> = triggerKit(trigger).validate(trigger)
    fun validateCondition(condition: Condition): List<Finding> = conditionKit(condition).validate(condition)
    fun validateEnd(end: EndCondition): List<Finding> = endKit(end).validate(end)

    fun validateConditionSets(conditions: List<Condition>): List<Finding> =
        conditionKits.flatMap { kit ->
            val matching = conditions.filter { it::class == kit.type }
            if (matching.isEmpty()) emptyList()
            else validateSetUnchecked(kit, matching)
        }

    @Suppress("UNCHECKED_CAST")
    private fun validateSetUnchecked(kit: ConditionKit<out Condition>, conditions: List<Condition>): List<Finding> =
        (kit as ConditionKit<Condition>).validateSet(conditions)

    fun capabilitiesFor(routine: Routine): Set<com.cues.core.model.Capability> {
        return ActionRegistry.capabilitiesFor(routine.actions) +
            triggerKit(routine.trigger).capabilities(routine.trigger) +
            routine.conditions.flatMap { conditionKit(it).capabilities(it) } +
            routine.endConditions.flatMap { endKit(it).capabilities(it) }
    }
}
