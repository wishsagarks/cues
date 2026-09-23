package com.cues.core.review

/**
 * Curated starter sentences, checked into `resources/templates.txt`.
 *
 * A template is a suggestion for what to type, never a shortcut around
 * drafting, validation or review: its sentence still goes through the normal
 * drafter, which still names itself in [com.cues.core.model.Routine.draftedBy].
 */
data class Template(val label: String, val sentence: String)

object Templates {
    private const val RESOURCE = "/templates.txt"

    /** Parses `label|sentence` lines, skipping blanks and `#` comments. */
    fun load(): List<Template> {
        val stream = Templates::class.java.getResourceAsStream(RESOURCE) ?: return emptyList()
        return stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split("|", limit = 2)
                    if (parts.size != 2) null else Template(parts[0].trim(), parts[1].trim())
                }
                .toList()
        }
    }
}
