package com.cues.core.coach

import com.cues.core.model.Day
import com.cues.core.store.JsonFileStore
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class UsageLedgerTest {
    @Test
    fun `ledger prunes events older than fourteen days and wipe removes the rest`() {
        val root = createTempDirectory("ledger-test").toFile()
        try {
            var now = 1_800_000_000_000L
            val store = JsonFileStore(root, nowMillis = { now })
            store.appendLedger(LedgerEvent.ManualStart(540, Day.MON, now - 15 * DAY))
            store.appendLedger(LedgerEvent.ManualStart(540, Day.TUE, now))

            assertEquals(1, store.ledgerEvents().size)
            store.wipeLedger()
            assertEquals(0, store.ledgerEvents().size)
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object { const val DAY = 86_400_000L }
}
