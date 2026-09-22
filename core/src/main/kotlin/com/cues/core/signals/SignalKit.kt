package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.model.Condition
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.EndCondition
import com.cues.core.model.EventKind
import com.cues.core.model.Session
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import kotlin.reflect.KClass

/** A closed, compile-time description of one event signal. */
interface TriggerKit<T : Trigger> {
    val type: KClass<T>
    /** Key of the OS listener that can deliver this trigger; null for manual. */
    val adapterKey: String?
    val eventKinds: Set<EventKind>

    fun match(trigger: T, event: TriggerEvent): Reason
    fun reverses(trigger: T, event: TriggerEvent): Boolean
    fun listensFor(trigger: T, kind: EventKind): Boolean = kind in eventKinds
    fun semanticForm(trigger: T): String
    fun validate(trigger: T): List<Finding>
    fun capabilities(trigger: T): Set<com.cues.core.model.Capability>
    fun describe(trigger: T): String
    fun reviewText(trigger: T): String = describe(trigger)
    fun noun(trigger: T): String = describe(trigger)
    fun rehearsalEvents(trigger: T, atMillis: Long): List<TriggerEvent>
    fun reversalEvent(trigger: T, atMillis: Long): TriggerEvent? = null
}

/** A closed, compile-time description of one context gate. */
interface ConditionKit<C : Condition> {
    val type: KClass<C>

    fun evaluate(condition: C, snapshot: ContextSnapshot, freshness: FreshnessPolicy): Reason
    fun semanticForm(condition: C): String
    fun validate(condition: C): List<Finding>
    fun validateSet(conditions: List<C>): List<Finding> = emptyList()
    fun capabilities(condition: C): Set<com.cues.core.model.Capability>
    fun describe(condition: C): String
    fun reviewText(condition: C): String = describe(condition)
}

/** A closed, compile-time description of one session ending rule. */
interface EndKit<E : EndCondition> {
    val type: KClass<E>

    fun semanticForm(end: E): String
    fun validate(end: E): List<Finding> = emptyList()
    fun capabilities(end: E): Set<com.cues.core.model.Capability> = emptySet()
    fun describe(end: E): String
    fun reviewText(end: E): String = describe(end)

    /** Returns the next absolute deadline, or null for event/manual endings. */
    fun schedule(end: E, session: Session): Long? = null
}

/** Small shared helpers used by kits and kept outside the evaluator's dispatch. */
internal object SignalText {
    fun finding(field: String, message: String) =
        Finding(com.cues.core.compile.Severity.ERROR, field, message)
}
