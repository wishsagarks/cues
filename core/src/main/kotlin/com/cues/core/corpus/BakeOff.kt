package com.cues.core.corpus

import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.model.DraftSourceId

data class BakeOffRow(
    val drafter: DraftSourceId,
    val passCount: Int,
    val total: Int,
    val wrongMeaningAccepts: Int,
    val p50Millis: Long,
    val p95Millis: Long,
    val modelFile: String? = null,
    val runtime: String,
    val backend: String,
    val networkOn: Boolean = false,
)

data class BakeOffReport(val rows: List<BakeOffRow>) {
    fun render(): String = rows.joinToString("\n") { row ->
        "${row.drafter}: ${row.passCount}/${row.total}, wrong meaning ${row.wrongMeaningAccepts}, " +
            "p50 ${row.p50Millis}ms, p95 ${row.p95Millis}ms, ${row.runtime}/${row.backend}, network=${row.networkOn}"
    }
}

object BakeOff {
    suspend fun run(
        drafters: List<RoutineDrafter>,
        cases: List<CorpusCase>,
        nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    ): BakeOffReport = BakeOffReport(drafters.map { drafter ->
        val latencies = mutableListOf<Long>()
        var passed = 0
        var wrongMeaning = 0
        cases.forEach { case ->
            val started = nowMillis()
            val result = drafter.draft(case.input)
            latencies += (nowMillis() - started).coerceAtLeast(0)
            val checked = Corpus.check(case, result)
            if (checked.passed) passed++
            if (result is DraftResult.Drafted && !checked.passed) wrongMeaning++
        }
        val sorted = latencies.sorted()
        fun percentile(p: Double): Long = sorted.getOrElse(((sorted.size - 1) * p).toInt()) { 0L }
        BakeOffRow(
            drafter = drafter.id,
            passCount = passed,
            total = cases.size,
            wrongMeaningAccepts = wrongMeaning,
            p50Millis = percentile(.5),
            p95Millis = percentile(.95),
            runtime = if (drafter.id == DraftSourceId.GRAMMAR_PARSER) "kotlin" else "on-device",
            backend = if (drafter.id == DraftSourceId.GRAMMAR_PARSER) "grammar" else "llm",
        )
    })
}
