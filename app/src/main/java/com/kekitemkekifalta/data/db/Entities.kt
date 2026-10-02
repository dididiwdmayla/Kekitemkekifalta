package com.kekitemkekifalta.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Sync-ready rows: every table has a UUID id, householdId, updatedAt and deletedAt (soft delete).
 * Never hard-delete user data and never change a column without a Migration.
 */

object Side {
    /** kekitem: at home. */
    const val HAVE = "have"

    /** kekifalta: needs buying. */
    const val NEED = "need"
}

object EndReason {
    const val FINISHED = "finished"
    const val SPOILED = "spoiled"
}

@Entity(
    tableName = "items",
    indices = [Index("householdId"), Index("normalizedName"), Index("side")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val normalizedName: String,
    /** Sector.key */
    val sector: String,
    val quantity: Double?,
    val unit: String?,
    val note: String?,
    /** ClockType.key */
    val clockType: String,
    /** Days that [estimateQuantity] lasts (see core DurationEstimator). */
    val estimatedDays: Double,
    val estimateQuantity: Double?,
    @ColumnInfo(defaultValue = "0") val learnedCycles: Int,
    /** Side.HAVE or Side.NEED */
    val side: String,
    /** Start of the open cycle while side == HAVE; mirrors the open row in item_cycles. */
    val stockedAt: Long?,
    /** "Ainda tem" keeps the item out of suggestions until this time. */
    val snoozedUntil: Long?,
    /** Checked in market mode, waiting for "Concluir compra". */
    val inCartAt: Long?,
    val catalogKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** One stay of an item at home: from purchase until it ran out or spoiled. */
@Entity(
    tableName = "item_cycles",
    indices = [Index("householdId"), Index("itemId"), Index("endedAt")],
)
data class ItemCycleEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val itemId: String,
    val startedAt: Long,
    val endedAt: Long?,
    /** EndReason.*, null while open. */
    val endReason: String?,
    val quantity: Double?,
    val unit: String?,
    /** Expected duration when the cycle started, kept for history. */
    val expectedDays: Double,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "markets", indices = [Index("householdId")])
data class MarketEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "aisles", indices = [Index("householdId"), Index("marketId")])
data class AisleEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val marketId: String,
    val name: String,
    /** Route order inside the market. */
    val position: Int,
    /** Comma separated Sector keys. */
    val sectors: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** Per item, per market: where it sits on the shelf, optionally forcing an aisle. */
@Entity(tableName = "shelf_notes", indices = [Index("householdId"), Index("itemId"), Index("marketId")])
data class ShelfNoteEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val itemId: String,
    val marketId: String,
    val note: String,
    val aisleId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** Waste screen row. */
data class SpoiledRow(
    val cycleId: String,
    val itemId: String,
    val itemName: String,
    val sector: String,
    val startedAt: Long,
    val endedAt: Long,
    val quantity: Double?,
    val unit: String?,
)
