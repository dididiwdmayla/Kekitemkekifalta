package com.kekitemkekifalta.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * What the app believes about an item's duration.
 *
 * @property days how many days [referenceQuantity] lasts.
 * @property referenceQuantity the quantity [days] refers to; null means "one usual purchase".
 * @property learnedCycles how many finished cycles shaped the estimate (drives the learning rate).
 */
data class Estimate(
    val days: Double,
    val referenceQuantity: Double?,
    val learnedCycles: Int,
)

/**
 * Adaptive duration learning.
 *
 * Each observed cycle is blended into the estimate in log space (so "twice as long" and "half as
 * long" weigh the same), like an exponentially weighted moving average:
 *  - the learning rate starts high and settles at [SETTLED_RATE], so the estimate converges in
 *    3–4 cycles and recent cycles always weigh more than old ones;
 *  - observations far from the estimate are damped: beyond a threshold only
 *    [OUTLIER_EXCESS_WEIGHT] of the excess counts. The threshold is looser while the estimate is
 *    young (catalog guesses can be far off) and tighter once it has been learned.
 *
 * Quantity: for items that get used up ([ClockType.RUNS_OUT]) duration scales with the quantity
 * bought relative to [Estimate.referenceQuantity]. Shelf life ([ClockType.SPOILS]) does not grow
 * with quantity.
 */
object DurationEstimator {
    const val MIN_DAYS = 0.25
    const val MAX_DAYS = 3650.0
    const val SETTLED_RATE = 0.4
    const val OUTLIER_EXCESS_WEIGHT = 0.15

    /** Spoiled items: aim a bit below what was observed, so the warning comes earlier next time. */
    const val SPOIL_SHRINK = 0.85

    /** "Ainda tem": it lasts at least this much longer than the time already elapsed. */
    const val STILL_HAS_STRETCH = 1.25

    /** Cycles shorter than this fraction of the expected duration are treated as mistakes. */
    const val MIN_LEARNABLE_FRACTION = 0.1
    const val MIN_LEARNABLE_DAYS = 2.0 / 24.0

    fun learningRate(learnedCycles: Int): Double = when {
        learnedCycles <= 0 -> 0.7
        learnedCycles == 1 -> 0.6
        learnedCycles == 2 -> 0.5
        else -> SETTLED_RATE
    }

    fun outlierThreshold(learnedCycles: Int): Double = when {
        learnedCycles <= 0 -> 3.5
        learnedCycles == 1 -> 2.5
        learnedCycles == 2 -> 1.8
        else -> 1.6
    }

    fun initial(days: Double, quantity: Double?): Estimate =
        Estimate(clampDays(days), quantity?.takeIf { it > 0 }, 0)

    fun quantityFactor(clock: ClockType, quantity: Double?, referenceQuantity: Double?): Double {
        if (clock == ClockType.SPOILS) return 1.0
        if (quantity == null || referenceQuantity == null || quantity <= 0 || referenceQuantity <= 0) return 1.0
        return (quantity / referenceQuantity).coerceIn(0.05, 50.0)
    }

    /** Expected duration in days of a cycle holding [quantity]. */
    fun expectedDays(estimate: Estimate, clock: ClockType, quantity: Double?): Double =
        estimate.days * quantityFactor(clock, quantity, estimate.referenceQuantity)

    fun isLearnable(elapsedDays: Double, expectedDays: Double): Boolean =
        elapsedDays >= MIN_LEARNABLE_DAYS && elapsedDays >= expectedDays * MIN_LEARNABLE_FRACTION

    /** "Acabou": the cycle lasted [elapsedDays]. */
    fun onFinished(estimate: Estimate, clock: ClockType, quantity: Double?, elapsedDays: Double): Estimate {
        val expected = expectedDays(estimate, clock, quantity)
        if (!isLearnable(elapsedDays, expected)) return estimate
        val observed = elapsedDays / quantityFactor(clock, quantity, estimate.referenceQuantity)
        return estimate.copy(
            days = blend(estimate.days, observed, estimate.learnedCycles),
            learnedCycles = estimate.learnedCycles + 1,
        )
    }

    /** "Estragou": always lowers the estimate. */
    fun onSpoiled(estimate: Estimate, clock: ClockType, quantity: Double?, elapsedDays: Double): Estimate {
        val expected = expectedDays(estimate, clock, quantity)
        if (!isLearnable(elapsedDays, expected)) {
            return estimate.copy(days = clampDays(estimate.days * 0.9))
        }
        val observed = elapsedDays / quantityFactor(clock, quantity, estimate.referenceQuantity)
        val target = min(observed, estimate.days) * SPOIL_SHRINK
        return estimate.copy(
            days = blend(estimate.days, target, estimate.learnedCycles),
            learnedCycles = estimate.learnedCycles + 1,
        )
    }

    /**
     * "Ainda tem" on a suggestion: the item outlived the prediction, so the estimate grows.
     * It is a partial observation, so [Estimate.learnedCycles] does not change.
     */
    fun onStillHas(estimate: Estimate, clock: ClockType, quantity: Double?, elapsedDays: Double): Estimate {
        val factor = quantityFactor(clock, quantity, estimate.referenceQuantity)
        val expected = estimate.days * factor
        val lowerBound = max(elapsedDays, expected) * STILL_HAS_STRETCH
        return estimate.copy(days = blend(estimate.days, lowerBound / factor, estimate.learnedCycles))
    }

    /** How long a "ainda tem" keeps the item out of the suggestions. */
    fun stillHasSnoozeDays(expectedDays: Double): Double = (expectedDays * 0.25).coerceIn(1.0, 7.0)

    /** Manual edit: [days] is how long [quantity] lasts. Worth about two observed cycles. */
    fun manual(estimate: Estimate, days: Double, quantity: Double?): Estimate =
        Estimate(clampDays(days), quantity?.takeIf { it > 0 }, max(estimate.learnedCycles, 2))

    internal fun blend(current: Double, observed: Double, learnedCycles: Int): Double {
        val c = clampDays(current)
        val o = clampDays(observed)
        val logRatio = ln(o / c)
        val threshold = ln(outlierThreshold(learnedCycles))
        val damped = if (abs(logRatio) <= threshold) {
            logRatio
        } else {
            sign(logRatio) * (threshold + (abs(logRatio) - threshold) * OUTLIER_EXCESS_WEIGHT)
        }
        return clampDays(c * exp(learningRate(learnedCycles) * damped))
    }

    private fun clampDays(days: Double): Double =
        if (days.isNaN()) MIN_DAYS else days.coerceIn(MIN_DAYS, MAX_DAYS)
}
