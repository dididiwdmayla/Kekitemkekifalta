package com.kekitemkekifalta.data

import com.kekitemkekifalta.core.ClockType
import com.kekitemkekifalta.core.DurationEstimator
import com.kekitemkekifalta.core.Estimate
import com.kekitemkekifalta.core.QuantityFormat
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.StockClock
import com.kekitemkekifalta.core.StockState
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.Side
import com.kekitemkekifalta.core.AisleSpec

val ItemEntity.sectorEnum: Sector get() = Sector.fromKey(sector)
val ItemEntity.clock: ClockType get() = ClockType.fromKey(clockType)
val ItemEntity.isHave: Boolean get() = side == Side.HAVE
val ItemEntity.isNeed: Boolean get() = side == Side.NEED
val ItemEntity.estimate: Estimate get() = Estimate(estimatedDays, estimateQuantity, learnedCycles)
val ItemEntity.expectedDays: Double get() = DurationEstimator.expectedDays(estimate, clock, quantity)
val ItemEntity.quantityLabel: String? get() = QuantityFormat.format(quantity, unit)

/** Null when the item is not at home. */
fun ItemEntity.stockState(now: Long): StockState? =
    if (side == Side.HAVE && stockedAt != null) StockClock.state(stockedAt, now, expectedDays, snoozedUntil) else null

fun AisleEntity.toSpec(): AisleSpec = AisleSpec(id, name, Sector.decodeList(sectors))
