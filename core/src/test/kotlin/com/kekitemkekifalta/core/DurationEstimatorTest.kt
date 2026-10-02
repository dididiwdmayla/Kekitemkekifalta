package com.kekitemkekifalta.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DurationEstimatorTest {

    private fun finishMany(start: Estimate, observations: List<Double>, clock: ClockType = ClockType.RUNS_OUT, quantity: Double? = null): Estimate =
        observations.fold(start) { e, days -> DurationEstimator.onFinished(e, clock, quantity, days) }

    private fun relativeError(value: Double, truth: Double) = abs(value - truth) / truth

    @Test
    fun convergesWithinFourCyclesFromAFourTimesWrongGuess() {
        val start = DurationEstimator.initial(30.0, null)
        val after3 = finishMany(start, List(3) { 7.0 })
        val after4 = finishMany(start, List(4) { 7.0 })
        assertTrue(relativeError(after3.days, 7.0) < 0.2, "after 3 cycles: ${after3.days}")
        assertTrue(relativeError(after4.days, 7.0) < 0.1, "after 4 cycles: ${after4.days}")
        assertEquals(4, after4.learnedCycles)
    }

    @Test
    fun convergesFastFromATwoTimesWrongGuess() {
        val after3 = finishMany(DurationEstimator.initial(20.0, null), List(3) { 10.0 })
        assertTrue(relativeError(after3.days, 10.0) < 0.07, "after 3 cycles: ${after3.days}")
    }

    @Test
    fun convergesUpwardsToo() {
        val after4 = finishMany(DurationEstimator.initial(4.0, null), List(4) { 12.0 })
        assertTrue(relativeError(after4.days, 12.0) < 0.12, "after 4 cycles: ${after4.days}")
    }

    @Test
    fun recentCyclesWeighMoreThanOldOnes() {
        val settled = finishMany(DurationEstimator.initial(10.0, null), List(6) { 10.0 })
        // Habit changed: now it lasts 5 days. Two recent cycles should already pull hard.
        val changed = finishMany(settled, listOf(5.0, 5.0))
        assertTrue(changed.days < 7.5, "should move towards the recent behaviour: ${changed.days}")
    }

    @Test
    fun outlierIsDampedOnceTheEstimateIsLearned() {
        val settled = finishMany(DurationEstimator.initial(10.0, null), List(5) { 10.0 })
        val afterTrip = DurationEstimator.onFinished(settled, ClockType.RUNS_OUT, null, 40.0)
        // A plain 0.4 EWMA in log space would jump to ~17.4 days.
        assertTrue(afterTrip.days < 13.5, "outlier should be damped: ${afterTrip.days}")
        assertTrue(afterTrip.days > settled.days, "but still counts a bit")
        // And it comes back quickly.
        val back = finishMany(afterTrip, listOf(10.0, 10.0))
        assertTrue(relativeError(back.days, 10.0) < 0.1, "recovers: ${back.days}")
    }

    @Test
    fun youngEstimateIsDampedLessThanLearnedOne() {
        val young = DurationEstimator.blend(10.0, 40.0, 0)
        val learned = DurationEstimator.blend(10.0, 40.0, 5)
        assertTrue(young > learned)
    }

    @Test
    fun quantityScalesConsumablesProportionally() {
        val e = DurationEstimator.initial(30.0, 1.0)
        assertEquals(60.0, DurationEstimator.expectedDays(e, ClockType.RUNS_OUT, 2.0), 1e-9)
        assertEquals(15.0, DurationEstimator.expectedDays(e, ClockType.RUNS_OUT, 0.5), 1e-9)
        // Two packs lasting 60 days means one pack still lasts 30: nothing to learn.
        val learned = DurationEstimator.onFinished(e, ClockType.RUNS_OUT, 2.0, 60.0)
        assertEquals(30.0, learned.days, 1e-9)
    }

    @Test
    fun quantityDoesNotStretchShelfLife() {
        val e = DurationEstimator.initial(4.0, 1.0)
        assertEquals(4.0, DurationEstimator.expectedDays(e, ClockType.SPOILS, 3.0), 1e-9)
    }

    @Test
    fun missingQuantityMeansOneUsualPurchase() {
        val e = DurationEstimator.initial(30.0, null)
        assertEquals(30.0, DurationEstimator.expectedDays(e, ClockType.RUNS_OUT, 2.0), 1e-9)
    }

    @Test
    fun spoiledAlwaysLowersTheEstimate() {
        val e = DurationEstimator.initial(4.0, null)
        listOf(1.0, 3.0, 4.0, 6.0, 20.0).forEach { elapsed ->
            val after = DurationEstimator.onSpoiled(e, ClockType.SPOILS, null, elapsed)
            assertTrue(after.days < e.days, "spoiled after $elapsed days should lower 4.0, got ${after.days}")
        }
        val tooSoon = DurationEstimator.onSpoiled(e, ClockType.SPOILS, null, 0.01)
        assertTrue(tooSoon.days < e.days)
    }

    @Test
    fun stillHasRaisesTheEstimateWithoutCountingACycle() {
        val e = finishMany(DurationEstimator.initial(10.0, null), List(3) { 10.0 })
        val after = DurationEstimator.onStillHas(e, ClockType.RUNS_OUT, null, 11.0)
        assertTrue(after.days > e.days)
        assertEquals(e.learnedCycles, after.learnedCycles)
    }

    @Test
    fun accidentalShortCycleIsIgnored() {
        val e = DurationEstimator.initial(30.0, null)
        val after = DurationEstimator.onFinished(e, ClockType.RUNS_OUT, null, 0.02)
        assertEquals(e, after)
    }

    @Test
    fun manualEditSetsDaysAndReference() {
        val e = DurationEstimator.initial(30.0, null)
        val manual = DurationEstimator.manual(e, 12.0, 2.0)
        assertEquals(12.0, manual.days, 1e-9)
        assertEquals(2.0, manual.referenceQuantity)
        assertEquals(2, manual.learnedCycles)
    }

    @Test
    fun estimateStaysWithinBounds() {
        var e = DurationEstimator.initial(1.0, null)
        repeat(20) { e = DurationEstimator.onSpoiled(e, ClockType.SPOILS, null, 0.3) }
        assertTrue(e.days >= DurationEstimator.MIN_DAYS)
        var big = DurationEstimator.initial(3000.0, null)
        repeat(20) { big = DurationEstimator.onStillHas(big, ClockType.RUNS_OUT, null, 5000.0) }
        assertTrue(big.days <= DurationEstimator.MAX_DAYS)
    }
}
