package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.compile.Severity
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.eval.conjoin
import com.cues.core.eval.describe
import com.cues.core.eval.freshened
import com.cues.core.model.*
import com.cues.core.ports.NamedContextStore
import com.cues.core.ports.PlaceStore
import kotlin.reflect.KClass

/** Explicit resolution is a port; this holder only makes the existing closed registry usable from legacy call sites. */
object ContextualStores {
    private object EmptyContexts : NamedContextStore {
        override fun findContext(id: String) = null
        override fun allContexts() = emptyList<NamedContext>()
        override fun saveContext(context: NamedContext) = Unit
        override fun deleteContext(id: String) = Unit
    }
    private object EmptyPlaces : PlaceStore {
        override fun findPlace(id: String) = null
        override fun allPlaces() = emptyList<Place>()
        override fun savePlace(place: Place) = Unit
        override fun deletePlace(id: String) = Unit
    }
    @Volatile var contexts: NamedContextStore = EmptyContexts
    @Volatile var places: PlaceStore = EmptyPlaces
}

object InContextKit : ConditionKit<Condition.InContext> {
    override val type: KClass<Condition.InContext> = Condition.InContext::class
    override fun evaluate(condition: Condition.InContext, snapshot: ContextSnapshot, freshness: FreshnessPolicy): Reason {
        val context = ContextualStores.contexts.findContext(condition.contextId)
            ?: return Reason(ReasonCode.CONTEXT_UNKNOWN, Truth.UNKNOWN, "Context ${condition.label} is no longer available.")
        if (context.version != condition.contextVersion) {
            return Reason(ReasonCode.CONTEXT_MISMATCH, Truth.UNKNOWN, "Context ${condition.label} changed and needs review.")
        }
        val member = context.predicates.map { SignalRegistry.evaluate(it, snapshot, freshness) }
        val truth = member.map { it.truth }.conjoin()
        val first = member.firstOrNull { it.truth != Truth.MATCH }
        return when (truth) {
            Truth.MATCH -> Reason(ReasonCode.CONTEXT_MATCHED, Truth.MATCH, "All parts of ${context.label} matched.")
            Truth.NO_MATCH -> Reason(ReasonCode.CONTEXT_NOT_MATCHED, Truth.NO_MATCH, "${context.label}: ${first?.detail ?: "a condition did not match"}")
            Truth.UNKNOWN -> Reason(ReasonCode.CONTEXT_UNKNOWN, Truth.UNKNOWN, "${context.label}: ${first?.detail ?: "a signal could not be read"}")
        }
    }
    override fun semanticForm(condition: Condition.InContext): String {
        val context = ContextualStores.contexts.findContext(condition.contextId)
        return if (context == null) "context:missing:${condition.contextId}:${condition.contextVersion}" else
            "context:${context.id}:${context.version}:[${context.predicates.sortedBy { SignalRegistry.semanticForm(it) }.joinToString("|") { SignalRegistry.semanticForm(it) }}]"
    }
    override fun validate(condition: Condition.InContext): List<Finding> {
        val context = ContextualStores.contexts.findContext(condition.contextId)
        return when {
            context == null -> listOf(Finding(Severity.ERROR, "conditions.context", "Context ${condition.label} was not found."))
            context.version != condition.contextVersion -> listOf(Finding(Severity.ERROR, "conditions.context", "Context ${condition.label} changed; review this cue again."))
            context.predicates.any { it is Condition.InContext } -> listOf(Finding(Severity.ERROR, "contexts.${context.label}", "Contexts cannot contain another context."))
            else -> context.predicates.flatMap { SignalRegistry.validateCondition(it) } + SignalRegistry.validateConditionSets(context.predicates)
        }
    }
    override fun capabilities(condition: Condition.InContext): Set<Capability> =
        ContextualStores.contexts.findContext(condition.contextId)?.predicates
            ?.flatMap { SignalRegistry.conditionKit(it).capabilities(it) }?.toSet().orEmpty()
    override fun describe(condition: Condition.InContext) = "in ${condition.label}"
    override fun reviewText(condition: Condition.InContext) = "you are in ${condition.label}"
}

object AudioOutputKit : TriggerKit<Trigger.AudioOutput> {
    override val type = Trigger.AudioOutput::class
    override val adapterKey = "audio-output"
    override val eventKinds = setOf(EventKind.AUDIO_OUTPUT_ADDED, EventKind.AUDIO_OUTPUT_REMOVED)
    override fun match(trigger: Trigger.AudioOutput, event: TriggerEvent): Reason {
        val wanted = if (trigger.transition == AudioTransition.ADDED) EventKind.AUDIO_OUTPUT_ADDED else EventKind.AUDIO_OUTPUT_REMOVED
        val kind = event.deviceId?.let { runCatching { AudioKind.valueOf(it) }.getOrNull() }
        return when {
            event.kind != wanted -> Reason(ReasonCode.TRIGGER_KIND_MISMATCH, Truth.NO_MATCH, "This was not the selected audio transition.")
            trigger.kind != AudioKind.ANY && kind != trigger.kind -> Reason(ReasonCode.AUDIO_KIND_MISMATCH, Truth.NO_MATCH, "This was not ${trigger.kind.name.lowercase()} audio.")
            else -> Reason(ReasonCode.AUDIO_OUTPUT_CHANGED, Truth.MATCH, "${trigger.kind.name.lowercase().replaceFirstChar(Char::uppercase)} audio ${trigger.transition.name.lowercase()}.")
        }
    }
    override fun reverses(trigger: Trigger.AudioOutput, event: TriggerEvent) =
        trigger.transition == AudioTransition.ADDED && event.kind == EventKind.AUDIO_OUTPUT_REMOVED
    override fun semanticForm(trigger: Trigger.AudioOutput) = "audio:${trigger.transition}:${trigger.kind}"
    override fun validate(trigger: Trigger.AudioOutput) = emptyList<Finding>()
    override fun capabilities(trigger: Trigger.AudioOutput) = emptySet<Capability>()
    override fun describe(trigger: Trigger.AudioOutput) = "${trigger.kind.name.lowercase()} audio ${trigger.transition.name.lowercase()}"
    override fun rehearsalEvents(trigger: Trigger.AudioOutput, atMillis: Long) = listOf(TriggerEvent(
        if (trigger.transition == AudioTransition.ADDED) EventKind.AUDIO_OUTPUT_ADDED else EventKind.AUDIO_OUTPUT_REMOVED,
        atMillis, deviceId = trigger.kind.name, provenance = EventProvenance.REHEARSAL,
    ))
    override fun reversalEvent(trigger: Trigger.AudioOutput, atMillis: Long) = TriggerEvent(EventKind.AUDIO_OUTPUT_REMOVED, atMillis, deviceId = trigger.kind.name, provenance = EventProvenance.REHEARSAL)
}

object AudioOutputActiveKit : ConditionKit<Condition.AudioOutputActive> {
    override val type = Condition.AudioOutputActive::class
    override fun evaluate(condition: Condition.AudioOutputActive, snapshot: ContextSnapshot, freshness: FreshnessPolicy): Reason = when (val outputs = snapshot.audioOutputs) {
        is ContextValue.Unknown -> Reason(ReasonCode.AUDIO_OUTPUT_UNKNOWN, Truth.UNKNOWN, "Audio output could not be read (${outputs.reason.describe()}).")
        is ContextValue.Known -> if (condition.kind == AudioKind.ANY && outputs.value.isNotEmpty() || condition.kind in outputs.value)
            Reason(ReasonCode.AUDIO_OUTPUT_ACTIVE, Truth.MATCH, "${condition.kind.name.lowercase()} audio is active.")
        else Reason(ReasonCode.AUDIO_OUTPUT_INACTIVE, Truth.NO_MATCH, "${condition.kind.name.lowercase()} audio is not active.")
    }
    override fun semanticForm(condition: Condition.AudioOutputActive) = "audioActive:${condition.kind}"
    override fun validate(condition: Condition.AudioOutputActive) = emptyList<Finding>()
    override fun capabilities(condition: Condition.AudioOutputActive) = emptySet<Capability>()
    override fun describe(condition: Condition.AudioOutputActive) = "${condition.kind.name.lowercase()} audio active"
}

abstract class BatteryThresholdKit<C : Condition> : ConditionKit<C> {
    abstract fun threshold(condition: C): Int
    abstract fun matches(value: Int, threshold: Int): Boolean
    abstract fun name(condition: C): String
    override fun evaluate(condition: C, snapshot: ContextSnapshot, freshness: FreshnessPolicy): Reason = when (val battery = snapshot.batteryPercent.freshened(snapshot.nowMillis, freshness.chargingMaxAgeMillis)) {
        is ContextValue.Unknown -> Reason(ReasonCode.BATTERY_UNKNOWN, Truth.UNKNOWN, "Battery level could not be read (${battery.reason.describe()}).")
        is ContextValue.Known -> if (matches(battery.value, threshold(condition))) Reason(ReasonCode.BATTERY_AS_REQUIRED, Truth.MATCH, "Battery is ${battery.value}%, as required.")
        else Reason(ReasonCode.BATTERY_NOT_AS_REQUIRED, Truth.NO_MATCH, "Battery is ${battery.value}%, not ${name(condition)}.")
    }
    override fun validate(condition: C) = if (threshold(condition) !in 0..100) listOf(Finding(Severity.ERROR, "conditions.battery", "Battery percentage must be 0 to 100.")) else emptyList()
    override fun capabilities(condition: C) = setOf(Capability.BATTERY_STATE)
}
object BatteryBelowKit : BatteryThresholdKit<Condition.BatteryBelow>() {
    override val type = Condition.BatteryBelow::class
    override fun threshold(condition: Condition.BatteryBelow) = condition.percent
    override fun matches(value: Int, threshold: Int) = value < threshold
    override fun name(condition: Condition.BatteryBelow) = "below ${condition.percent}%"
    override fun semanticForm(condition: Condition.BatteryBelow) = "batteryBelow:${condition.percent}"
    override fun describe(condition: Condition.BatteryBelow) = "battery below ${condition.percent}%"
}
object BatteryAtLeastKit : BatteryThresholdKit<Condition.BatteryAtLeast>() {
    override val type = Condition.BatteryAtLeast::class
    override fun threshold(condition: Condition.BatteryAtLeast) = condition.percent
    override fun matches(value: Int, threshold: Int) = value >= threshold
    override fun name(condition: Condition.BatteryAtLeast) = "at least ${condition.percent}%"
    override fun semanticForm(condition: Condition.BatteryAtLeast) = "batteryAtLeast:${condition.percent}"
    override fun describe(condition: Condition.BatteryAtLeast) = "battery at least ${condition.percent}%"
}

private object PlaceSupport {
    fun match(trigger: Trigger.PlaceTransition, event: TriggerEvent): Reason {
        val wanted = if (trigger.transition == PlaceTransitionKind.ENTER) EventKind.PLACE_ENTERED else EventKind.PLACE_EXITED
        return if (event.kind == wanted && event.deviceId == trigger.placeId) Reason(ReasonCode.PLACE_ENTERED, Truth.MATCH, "${trigger.label}: ${trigger.transition.name.lowercase()}.")
        else Reason(ReasonCode.PLACE_MISMATCH, Truth.NO_MATCH, "This was not the selected place transition.")
    }
    fun reverses(trigger: Trigger.PlaceTransition, event: TriggerEvent) = trigger.transition == PlaceTransitionKind.ENTER && event.kind == EventKind.PLACE_EXITED && event.deviceId == trigger.placeId
    fun triggerSemanticForm(trigger: Trigger.PlaceTransition) = "place:${placeForm(trigger.placeId, trigger.placeVersion)}:${trigger.transition}"
    fun validateTrigger(trigger: Trigger.PlaceTransition) = validatePlace(trigger.placeId, trigger.placeVersion, trigger.label)
    fun evaluate(condition: Condition.AtPlace, snapshot: ContextSnapshot): Reason = when (val places = snapshot.insidePlaces) {
        is ContextValue.Unknown -> Reason(ReasonCode.PLACE_UNKNOWN, Truth.UNKNOWN, "${condition.label} could not be read (${places.reason.describe()}).")
        is ContextValue.Known -> if (condition.placeId in places.value) Reason(ReasonCode.PLACE_ENTERED, Truth.MATCH, "You are at ${condition.label}.") else Reason(ReasonCode.PLACE_NOT_INSIDE, Truth.NO_MATCH, "You are not at ${condition.label}.")
    }
    fun conditionSemanticForm(condition: Condition.AtPlace) = "atPlace:${placeForm(condition.placeId, condition.placeVersion)}"
    fun validateCondition(condition: Condition.AtPlace) = validatePlace(condition.placeId, condition.placeVersion, condition.label)
    private fun validatePlace(id: String, version: Int, label: String): List<Finding> {
        val place = ContextualStores.places.findPlace(id)
        return when {
            place == null -> listOf(Finding(Severity.ERROR, "place", "Place $label was not found."))
            place.version != version -> listOf(Finding(Severity.ERROR, "place", "Place $label changed; review this cue again."))
            place.radiusMeters !in 100..1000 -> listOf(Finding(Severity.ERROR, "place.radius", "Use a radius between 100 and 1000 metres; smaller geofences are not accurate enough."))
            else -> emptyList()
        }
    }
    private fun placeForm(id: String, version: Int): String = ContextualStores.places.findPlace(id)?.let { "${it.id}:${it.version}:${it.latitude}:${it.longitude}:${it.radiusMeters}" } ?: "missing:$id:$version"
}

object PlaceTransitionKit : TriggerKit<Trigger.PlaceTransition> {
    override val type = Trigger.PlaceTransition::class
    override val adapterKey = "place"
    override val eventKinds = setOf(EventKind.PLACE_ENTERED, EventKind.PLACE_EXITED)
    override fun match(trigger: Trigger.PlaceTransition, event: TriggerEvent) = PlaceSupport.match(trigger, event)
    override fun reverses(trigger: Trigger.PlaceTransition, event: TriggerEvent) = PlaceSupport.reverses(trigger, event)
    override fun semanticForm(trigger: Trigger.PlaceTransition) = PlaceSupport.triggerSemanticForm(trigger)
    override fun validate(trigger: Trigger.PlaceTransition) = PlaceSupport.validateTrigger(trigger)
    // Delivery is a foreground service (PlaceWatcherService), not a
    // GeofencingClient/ACCESS_BACKGROUND_LOCATION callback that fires while
    // Cues is killed — so only foreground location is actually needed.
    // Requiring LOCATION_BACKGROUND here would show it in Review as a
    // permanently-ungrantable requirement (no grant button exists for it)
    // and the cue could never arm. See CLEANUP.md CL-13.
    override fun capabilities(trigger: Trigger.PlaceTransition) = setOf(Capability.LOCATION_FOREGROUND)
    override fun describe(trigger: Trigger.PlaceTransition) = "${trigger.transition.name.lowercase()} ${trigger.label}"
    override fun rehearsalEvents(trigger: Trigger.PlaceTransition, atMillis: Long) = listOf(TriggerEvent(if (trigger.transition == PlaceTransitionKind.ENTER) EventKind.PLACE_ENTERED else EventKind.PLACE_EXITED, atMillis, deviceId = trigger.placeId, provenance = EventProvenance.REHEARSAL))
    override fun reversalEvent(trigger: Trigger.PlaceTransition, atMillis: Long) = TriggerEvent(EventKind.PLACE_EXITED, atMillis, deviceId = trigger.placeId, provenance = EventProvenance.REHEARSAL)
}

object AtPlaceKit : ConditionKit<Condition.AtPlace> {
    override val type = Condition.AtPlace::class
    override fun evaluate(condition: Condition.AtPlace, snapshot: ContextSnapshot, freshness: FreshnessPolicy) = PlaceSupport.evaluate(condition, snapshot)
    override fun semanticForm(condition: Condition.AtPlace) = PlaceSupport.conditionSemanticForm(condition)
    override fun validate(condition: Condition.AtPlace) = PlaceSupport.validateCondition(condition)
    override fun capabilities(condition: Condition.AtPlace) = setOf(Capability.LOCATION_FOREGROUND)
    override fun describe(condition: Condition.AtPlace) = "at ${condition.label}"
}
