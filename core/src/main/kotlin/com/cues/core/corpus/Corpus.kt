package com.cues.core.corpus

import com.cues.core.drafting.DraftResult
import com.cues.core.model.*

/**
 * One scored paraphrase: an input sentence and the meaning it must preserve.
 *
 * This lives in main rather than test on purpose. At the event the on-device
 * model is scored by the same harness, from the app, on the phone — so the
 * sentence "the model handled 17 of 19 paraphrases" means the same thing as
 * the parser's score rather than being a separately-invented number.
 */
data class CorpusCase(
    val input: String,
    val expectations: Map<String, String>,
) {
    val expectsClarification: Boolean get() = "clarify" in expectations
    val expectsUnsupported: Boolean get() = expectations["unsupported"] == "true"
}

data class CaseResult(
    val case: CorpusCase,
    val passed: Boolean,
    val failures: List<String>,
)

data class CorpusReport(
    val results: List<CaseResult>,
    val elapsedMillis: Long,
) {
    val passed: Int get() = results.count { it.passed }
    val total: Int get() = results.size

    /** Deliberately plain. A score is a measurement, not a headline. */
    fun summary(): String = "$passed/$total paraphrases preserved their meaning (${elapsedMillis}ms)"
}

object Corpus {

    /** Parses the corpus file format. Blank lines and `#` comments are ignored. */
    fun parse(text: String): List<CorpusCase> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val (input, expectations) = line.split("|", limit = 2).let {
                if (it.size == 2) it[0].trim() to it[1] else return@mapNotNull null
            }
            val parsed = expectations.split(",")
                .mapNotNull { pair ->
                    val kv = pair.split("=", limit = 2)
                    if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
                }
                .toMap()
            CorpusCase(input, parsed)
        }
        .toList()

    /**
     * Checks a draft against what the sentence was supposed to mean.
     *
     * Returns the specific mismatches rather than a bare boolean, because at
     * 3am the useful output is "kept the device, lost the ending", not "fail".
     */
    fun check(case: CorpusCase, result: DraftResult): CaseResult {
        val failures = mutableListOf<String>()

        // Checked before result shape: a request Cues cannot honour may come
        // back either as a partial draft with the rest reported, or as a
        // question that names the limitation. Both are honest; silence is not.
        if (case.expectsUnsupported) {
            val reported = when (result) {
                is DraftResult.Drafted -> result.unsupported
                is DraftResult.NeedsClarification -> result.unsupported
                is DraftResult.Failed -> emptyList()
            }
            if (reported.isEmpty()) {
                failures += "expected the unsupported part of the request to be reported"
            }
            if (result is DraftResult.NeedsClarification) {
                // Nothing was drafted, so there is no routine to check further.
                return CaseResult(case, failures.isEmpty(), failures)
            }
        }

        if (case.expectsClarification) {
            if (result !is DraftResult.NeedsClarification) {
                failures += "expected a question about ${case.expectations["clarify"]}, got ${result::class.simpleName}"
            }
            return CaseResult(case, failures.isEmpty(), failures)
        }

        if (result !is DraftResult.Drafted) {
            failures += "expected a drafted cue, got ${result::class.simpleName}"
            return CaseResult(case, false, failures)
        }

        val routine = result.routine

        case.expectations.forEach { (key, expected) ->
            when (key) {
                "trigger" -> checkTrigger(routine.trigger, expected)?.let { failures += it }
                "device" -> if (routine.trigger !is Trigger.BluetoothConnection) {
                    failures += "expected a bluetooth device trigger"
                }

                "days" -> checkDays(routine.conditions, expected)?.let { failures += it }
                "from" -> checkWindow(routine.conditions, start = expected)?.let { failures += it }
                "to" -> checkWindow(routine.conditions, end = expected)?.let { failures += it }
                "timer" -> checkTimer(routine.actions, expected.toInt())?.let { failures += it }
                "dnd" -> if (routine.actions.none { it.actionId == ActionId.REQUEST_DND }) {
                    failures += "expected notifications to be quieted"
                }

                "endsOnDisconnect" -> if (EndCondition.TriggerReversed !in routine.endConditions) {
                    failures += "expected the cue to end on disconnect"
                }

                else -> Unit
            }
        }

        return CaseResult(case, failures.isEmpty(), failures)
    }

    private fun checkTrigger(trigger: Trigger, expected: String): String? {
        val actual = when (trigger) {
            is Trigger.BluetoothConnection -> when (trigger.transition) {
                DeviceTransition.CONNECTED -> "bluetooth-connect"
                DeviceTransition.DISCONNECTED -> "bluetooth-disconnect"
            }

            is Trigger.Charging -> when (trigger.transition) {
                PowerTransition.PLUGGED_IN -> "charging-on"
                PowerTransition.UNPLUGGED -> "charging-off"
            }

            Trigger.Manual -> "manual"
        }
        return if (actual == expected) null else "trigger was $actual, expected $expected"
    }

    private fun checkDays(conditions: List<Condition>, expected: String): String? {
        val actual = conditions.filterIsInstance<Condition.DaysOfWeek>().firstOrNull()?.days
            ?: return "expected a day condition ($expected), found none"

        val wanted = when (expected) {
            "weekdays" -> WEEKDAYS
            "weekend" -> WEEKEND
            else -> expected.split("+").mapNotNull { token ->
                Day.entries.firstOrNull { it.name.equals(token.trim(), ignoreCase = true) }
            }.toSet()
        }
        return if (actual == wanted) null else "days were $actual, expected $wanted"
    }

    private fun checkWindow(conditions: List<Condition>, start: String? = null, end: String? = null): String? {
        val window = conditions.filterIsInstance<Condition.TimeWindow>().firstOrNull()
            ?: return "expected a time window, found none"

        start?.let { if (window.startInclusive.toString() != it) return "window started at ${window.startInclusive}, expected $it" }
        end?.let { if (window.endExclusive.toString() != it) return "window ended at ${window.endExclusive}, expected $it" }
        return null
    }

    private fun checkTimer(actions: List<ActionSpec>, expectedMinutes: Int): String? {
        val timer = actions.firstOrNull { it.actionId == ActionId.START_FOCUS_TIMER }
            ?: return "expected a focus timer, found none"
        val minutes = (timer.args as? ActionArgs.FocusTimer)?.durationMinutes
        return if (minutes == expectedMinutes) null else "timer was $minutes minutes, expected $expectedMinutes"
    }
}
