package com.cues.core.coach

class CoachPolicy(private val stateStore: CoachStateStore) {
    fun next(candidates: List<Suggestion>, now: Long): Suggestion? {
        val state = stateStore.loadCoachState()
        if (state.lastSurfacedAtMillis?.let { sameLocalDay(it, now) } == true) return null
        val suggestion = candidates.firstOrNull { candidate ->
            when {
                candidate.patternKey !in state.mutes -> true
                state.mutes[candidate.patternKey] == null -> false
                else -> now >= checkNotNull(state.mutes[candidate.patternKey])
            }
        } ?: return null
        stateStore.saveCoachState(state.copy(lastSurfacedAtMillis = now))
        return suggestion
    }

    fun dismiss(patternKey: String, now: Long, permanent: Boolean) {
        val state = stateStore.loadCoachState()
        val until = if (permanent) null else now + 30L * 86_400_000L
        stateStore.saveCoachState(state.copy(mutes = state.mutes + (patternKey to until)))
    }

    private fun sameLocalDay(a: Long, b: Long): Boolean = a / 86_400_000L == b / 86_400_000L
}
