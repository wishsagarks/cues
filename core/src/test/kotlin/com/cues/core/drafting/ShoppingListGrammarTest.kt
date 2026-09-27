package com.cues.core.drafting

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ShoppingListGrammarTest {

    @Test
    fun `parses quantity, unit and item for each comma-separated entry`() {
        val items = ShoppingListGrammar.parse("2kg potato, 2kg tomato")
        assertEquals(listOf(ShoppingItem(2.0, "kg", "potato"), ShoppingItem(2.0, "kg", "tomato")), items)
    }

    @Test
    fun `a space between the quantity and unit is optional`() {
        assertEquals(listOf(ShoppingItem(2.0, "kg", "potato")), ShoppingListGrammar.parse("2 kg potato"))
        assertEquals(listOf(ShoppingItem(2.0, "kg", "potato")), ShoppingListGrammar.parse("2kg potato"))
    }

    @Test
    fun `an item with no unit is still recognized`() {
        assertEquals(listOf(ShoppingItem(6.0, null, "eggs")), ShoppingListGrammar.parse("6 eggs"))
    }

    @Test
    fun `fractional quantities and long-form units normalize`() {
        assertEquals(listOf(ShoppingItem(1.5, "l", "milk")), ShoppingListGrammar.parse("1.5 liters milk"))
        assertEquals(listOf(ShoppingItem(1.0, "kg", "sugar")), ShoppingListGrammar.parse("1 kilogram sugar"))
    }

    @Test
    fun `blank segments and stray commas are dropped, not crashed on`() {
        assertEquals(listOf(ShoppingItem(2.0, "kg", "potato")), ShoppingListGrammar.parse("2kg potato, , "))
    }

    @Test
    fun `the formatter renders a stable, deterministic string`() {
        val items = ShoppingListGrammar.parse("2kg potato, 2kg tomato")
        assertEquals("Buy: 2kg potato, 2kg tomato", ShoppingListFormatter.format(items))
    }

    @Test
    fun `formatting an empty list never crashes and says so honestly`() {
        assertEquals("Buy: (nothing was recognized)", ShoppingListFormatter.format(emptyList()))
    }

    @Test
    fun `a whole-number quantity never renders a trailing decimal`() {
        assertEquals("Buy: 2kg potato", ShoppingListFormatter.format(listOf(ShoppingItem(2.0, "kg", "potato"))))
    }
}
