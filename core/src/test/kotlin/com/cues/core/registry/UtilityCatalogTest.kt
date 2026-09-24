package com.cues.core.registry

import com.cues.core.model.UtilityId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class UtilityCatalogTest {

    @Test
    fun `every utility id has a definition`() {
        UtilityId.entries.forEach { id ->
            val definition = UtilityCatalog.definition(id)
            assertEquals(id, definition.id)
        }
        assertEquals(UtilityId.entries.size, UtilityCatalog.all.size)
    }

    @Test
    fun `rejects raw ids that are not in the closed list`() {
        assertFalse(UtilityCatalog.isKnownRawId("PAYMENTS"))
        assertFalse(UtilityCatalog.isKnownRawId(""))
        assertFalse(UtilityCatalog.isKnownRawId("eye_protection")) // case-sensitive: not the enum's own spelling
        UtilityId.entries.forEach { assertTrue(UtilityCatalog.isKnownRawId(it.name)) }
    }

    @Test
    fun `every entry declares the accessibility-binding mechanism it actually uses`() {
        // Every cataloged utility today has no known public API, so its
        // mechanism must be disclosed as the last-resort one, never assumed
        // to be a plain intent it doesn't have.
        UtilityCatalog.all.forEach { definition ->
            assertEquals(UtilityMechanism.ACCESSIBILITY_BINDING, definition.mechanism, definition.id.name)
        }
    }
}
