package com.cues.core.context

import com.cues.core.model.Routine
import com.cues.core.ports.RoutineStore
import com.cues.core.ports.Embedder
import kotlin.math.sqrt

sealed interface ReferenceResolution {
    data class Resolved(val routine: Routine) : ReferenceResolution
    data class NeedsClarification(val candidates: List<Routine>) : ReferenceResolution
    data class NeedsConfirmation(val candidates: List<Routine>) : ReferenceResolution
    data object NotFound : ReferenceResolution
}

/** Read-only resolver over context the user explicitly created in Cues. */
class PersonalIndex(
    private val routines: RoutineStore,
    private val embedder: Embedder? = null,
) {
    fun resolveRoutine(reference: String, lastRoutineId: String? = null): ReferenceResolution {
        val raw = reference.trim()
        val normalized = raw.lowercase().removePrefix("my ").removePrefix("the ").trim()
        if (normalized in setOf("this", "this cue", "that", "that one")) {
            return lastRoutineId?.let(routines::findRoutine)?.let(ReferenceResolution::Resolved)
                ?: ReferenceResolution.NotFound
        }

        val all = routines.all()
        all.firstOrNull { it.id.equals(normalized, ignoreCase = true) || it.title.equals(normalized, ignoreCase = true) }
            ?.let { return ReferenceResolution.Resolved(it) }

        val words = normalized.split(Regex("\\s+")).filter { it.length > 2 && it != "cue" }.toSet()
        val candidates = all.filter { routine ->
            val haystack = (routine.title + " " + routine.sourceText).lowercase()
            words.isNotEmpty() && words.all(haystack::contains)
        }
        return when (candidates.size) {
            0 -> ReferenceResolution.NotFound
            1 -> ReferenceResolution.Resolved(candidates.single())
            else -> ReferenceResolution.NeedsClarification(candidates)
        }.let { lexical ->
            if (lexical != ReferenceResolution.NotFound) lexical else embeddingCandidates(normalized, all)
        }
    }

    private fun embeddingCandidates(reference: String, all: List<Routine>): ReferenceResolution {
        val query = embedder?.embed(reference) ?: return ReferenceResolution.NotFound
        val ranked = all.mapNotNull { routine ->
            embedder.embed(routine.title + " " + routine.sourceText)?.let { routine to cosine(query, it) }
        }.filter { it.second > 0f }.sortedByDescending { it.second }.map { it.first }.take(3)
        return if (ranked.isEmpty()) ReferenceResolution.NotFound else ReferenceResolution.NeedsConfirmation(ranked)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var aa = 0f
        var bb = 0f
        for (index in a.indices) {
            dot += a[index] * b[index]
            aa += a[index] * a[index]
            bb += b[index] * b[index]
        }
        if (aa == 0f || bb == 0f) return 0f
        return (dot / (sqrt(aa.toDouble()) * sqrt(bb.toDouble()))).toFloat()
    }
}
