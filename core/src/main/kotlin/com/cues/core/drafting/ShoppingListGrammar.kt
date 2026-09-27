package com.cues.core.drafting

/** One "2kg potato"-shaped entry: a quantity, an optional unit, and the item name. */
data class ShoppingItem(val quantity: Double, val unit: String?, val item: String)

/**
 * Turns "2kg potato, 2kg tomato" into typed [ShoppingItem]s at draft time.
 *
 * Comma-separated on purpose, not "and": this app's grammar already uses
 * "and" to join separate actions in one sentence ("quiet notifications and
 * start a focus timer"), so reusing it inside a list would make "buy 2kg
 * potato and 2kg tomato and notify me" ambiguous about where the list ends.
 */
object ShoppingListGrammar {
    private val ITEM_PATTERN = Regex(
        "^(\\d+(?:\\.\\d+)?)\\s*(kg|kilograms?|g|grams?|l|liters?|litres?|ml|dozen|packs?)?\\s+(.+)$",
    )

    fun parse(text: String): List<ShoppingItem> =
        text.split(",").map { it.trim() }.filter { it.isNotBlank() }.mapNotNull(::parseItem)

    private fun parseItem(segment: String): ShoppingItem? {
        val match = ITEM_PATTERN.find(segment) ?: return null
        val quantity = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].takeIf { it.isNotBlank() }?.let(::normalizeUnit)
        val item = match.groupValues[3].trim().trimEnd('.', ',')
        return item.takeIf { it.isNotBlank() }?.let { ShoppingItem(quantity, unit, it) }
    }

    private fun normalizeUnit(raw: String): String = when (raw) {
        "kilogram", "kilograms" -> "kg"
        "gram", "grams" -> "g"
        "liter", "liters", "litre", "litres" -> "l"
        "pack", "packs" -> "pack"
        else -> raw
    }
}

/** Renders a stable, deterministic string from typed items — never model prose. */
object ShoppingListFormatter {
    fun format(items: List<ShoppingItem>): String {
        if (items.isEmpty()) return "Buy: (nothing was recognized)"
        return "Buy: " + items.joinToString(", ") { item ->
            val quantity = if (item.quantity == Math.floor(item.quantity)) {
                item.quantity.toLong().toString()
            } else {
                item.quantity.toString()
            }
            "$quantity${item.unit.orEmpty()} ${item.item}".trim()
        }
    }
}
