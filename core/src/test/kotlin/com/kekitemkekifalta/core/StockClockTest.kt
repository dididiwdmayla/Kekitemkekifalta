package com.kekitemkekifalta.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StockClockTest {
    private val day = StockClock.DAY_MS

    @Test
    fun statusFollowsElapsedFraction() {
        assertEquals(StockStatus.HAVE, StockClock.state(0, 2 * day, 4.0).status)
        assertEquals(StockStatus.RUNNING_LOW, StockClock.state(0, 3 * day, 4.0).status)
        assertEquals(StockStatus.PROBABLY_OUT, StockClock.state(0, 4 * day, 4.0).status)
        assertEquals(StockStatus.PROBABLY_OUT, StockClock.state(0, 9 * day, 4.0).status)
    }

    @Test
    fun snoozeHoldsBackProbablyOut() {
        val snoozed = StockClock.state(0, 5 * day, 4.0, snoozedUntil = 6 * day)
        assertEquals(StockStatus.RUNNING_LOW, snoozed.status)
        assertTrue(snoozed.snoozed)
        val expired = StockClock.state(0, 7 * day, 4.0, snoozedUntil = 6 * day)
        assertEquals(StockStatus.PROBABLY_OUT, expired.status)
        assertFalse(expired.snoozed)
    }

    @Test
    fun remainingLabels() {
        assertEquals("faltam ~3 dias", StockClock.remainingLabel(StockClock.state(0, 1 * day, 4.0)))
        assertEquals("acaba hoje", StockClock.remainingLabel(StockClock.state(0, 4 * day, 4.0)))
        assertEquals("passou 2 dias", StockClock.remainingLabel(StockClock.state(0, 6 * day, 4.0)))
    }
}
