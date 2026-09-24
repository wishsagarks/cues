package com.cues.core.coach

import com.cues.core.model.Day
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LedgerEvent {
    val atMillis: Long

    @Serializable @SerialName("sessionEnded")
    data class SessionEnded(
        val routineId: String,
        val plannedMinutes: Int,
        val actualMinutes: Int,
        val endReason: String,
        override val atMillis: Long,
    ) : LedgerEvent

    @Serializable @SerialName("skipped")
    data class Skipped(
        val reasonCode: String,
        val capability: String? = null,
        val weekday: Day? = null,
        override val atMillis: Long,
    ) : LedgerEvent

    @Serializable @SerialName("actionBlocked")
    data class ActionBlocked(val actionId: String, override val atMillis: Long) : LedgerEvent

    @Serializable @SerialName("patchCreated")
    data class PatchCreated(val kind: String, val weekday: Day, override val atMillis: Long) : LedgerEvent

    @Serializable @SerialName("manualStart")
    data class ManualStart(val minuteOfDay: Int, val weekday: Day, override val atMillis: Long) : LedgerEvent

    @Serializable @SerialName("signalObserved")
    data class SignalObserved(
        val kind: String,
        val key: String,
        val minuteOfDay: Int,
        val weekday: Day,
        override val atMillis: Long,
    ) : LedgerEvent

    @Serializable @SerialName("coverageGap")
    data class CoverageGap(val fromMillis: Long, val toMillis: Long, val why: String) : LedgerEvent {
        override val atMillis: Long get() = toMillis
    }
}

interface UsageLedger {
    val signalOptIn: Boolean
    fun setSignalOptIn(enabled: Boolean)
    fun appendLedger(event: LedgerEvent)
    fun ledgerEvents(): List<LedgerEvent>
    fun wipeLedger()
}

@Serializable
data class CoachState(
    val lastSurfacedAtMillis: Long? = null,
    val mutes: Map<String, Long?> = emptyMap(), // null value means permanent
)

interface CoachStateStore {
    fun loadCoachState(): CoachState
    fun saveCoachState(state: CoachState)
}
