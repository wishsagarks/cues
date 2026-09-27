package com.cues.core.assistant

/** Closed deterministic grammar for deciding which Cues subsystem may see a turn. */
class IntentRouter {
    fun route(input: String): AssistantIntent {
        val text = input.trim()
        val normalized = text.lowercase()

        Regex("^remember (?:my )?(.+?) is (.+)$", RegexOption.IGNORE_CASE).find(text)?.let {
            return AssistantIntent.Remember(it.groupValues[1].trim(), it.groupValues[2].trim())
        }

        if (normalized.matches(Regex(".*\\bwhat (?:can|do) you do\\??$")) || normalized == "capabilities") {
            return AssistantIntent.Capabilities
        }
        if (Regex("\\b(?:list|show) (?:my )?cues\\b").containsMatchIn(normalized)) return AssistantIntent.ListCues
        if (Regex("\\b(?:what|which).*(?:today|forecast)|\\btoday'?s forecast\\b").containsMatchIn(normalized)) {
            return AssistantIntent.Forecast
        }
        Regex("^why (?:didn'?t|did not) (.+?)(?: run)?(?: (yesterday|today))?\\??$").find(normalized)?.let {
            return AssistantIntent.Explain(it.groupValues[1].trim(), it.groupValues.getOrNull(2)?.ifBlank { null })
        }
        Regex("^(pause|resume|stop|skip today)(?:\\s+)(.+)$").find(normalized)?.let { match ->
            val kind = when (match.groupValues[1]) {
                "pause" -> ControlKind.PAUSE
                "resume" -> ControlKind.RESUME
                "stop" -> ControlKind.STOP
                else -> ControlKind.SKIP_TODAY
            }
            return AssistantIntent.Control(kind, text.substring(match.groups[2]!!.range).trim())
        }
        Regex("^(?:make (?:it|this)|set (?:it|this)(?: to)?)\\s+(\\d{1,3})\\s*(?:minutes?|mins?)$").find(normalized)?.let {
            return AssistantIntent.Refine(RefineOperation.SetDuration(it.groupValues[1].toInt()))
        }

        // Checked before SYSTEM_AGENT_PATTERNS on purpose: a request with an
        // unambiguous cue shape wins even when it also contains a generic
        // verb SYSTEM_AGENT_PATTERNS would otherwise catch — "when I miss a
        // call, whatsapp mom..." has "call" in it, but "when" already says
        // this is a rule, not a one-off "call someone now" task. Only a
        // request with no cue shape at all falls through to SYSTEM_AGENT_PATTERNS.
        if (CUE_PATTERNS.any { it.containsMatchIn(normalized) }) return AssistantIntent.Create(text)
        if (SYSTEM_AGENT_PATTERNS.any { it.containsMatchIn(normalized) }) {
            return AssistantIntent.Unsupported(text, UnsupportedRoute.SYSTEM_AGENT)
        }
        return AssistantIntent.Unsupported(text)
    }

    private companion object {
        val CUE_PATTERNS = listOf(
            Regex("\\bwhen\\b"), Regex("\\bevery\\b"), Regex("\\bweekdays?\\b"),
            Regex("\\b(?:plug|connect|disconnect|unplug)"), Regex("\\bstart (?:a )?\\d+[- ]minute"),
            Regex("\\bquiet notifications\\b"),
            // A bare "at 9:00" is a valid AtTime trigger in the grammar with
            // no "when" needed — this closed the gap where that phrasing fell
            // through to SYSTEM_AGENT_PATTERNS or the generic Unsupported.
            Regex("\\bat\\s+\\d{1,2}:\\d{2}\\b"),
            // "Automatically <channel> ... saying ..." is exclusively
            // SIMULATE_SEND's own phrasing — a real one-off request never
            // says "automatically" this way, so this can never falsely steal
            // a genuine Jovi task.
            Regex("\\bautomatically\\b"),
            // Distinguishes "when I miss a call" (a trigger) from a bare
            // "call someone" one-off task, which SYSTEM_AGENT_PATTERNS' plain
            // \bcall\b still catches on its own.
            Regex("\\bmiss(?:ed)?\\s+(?:a\\s+)?call\\b"),
            // Digit-qualified: "buy 2kg potato" is the shopping-list capture;
            // "buy me a pizza" (no digit) is still a genuine Jovi task and
            // correctly falls through to SYSTEM_AGENT_PATTERNS below.
            Regex("\\bbuy\\s+\\d"),
            Regex("\\bcheck\\s+(?:my\\s+|today'?s\\s+)?(?:daily\\s+)?mail\\b"),
        )
        val SYSTEM_AGENT_PATTERNS = listOf(
            Regex("\\b(?:book|order|buy|pay|send|message|call)\\b"),
            Regex("\\b(?:summari[sz]e|write|compile)\\b.*\\b(?:file|news|report|email)\\b"),
            Regex("\\b(?:ride|restaurant|flight|hotel)\\b"),
        )
    }
}
