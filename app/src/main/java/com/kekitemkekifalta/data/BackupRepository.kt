package com.kekitemkekifalta.data

import androidx.room.withTransaction
import com.kekitemkekifalta.core.AisleDto
import com.kekitemkekifalta.core.BackupCodec
import com.kekitemkekifalta.core.BackupFile
import com.kekitemkekifalta.core.CycleDto
import com.kekitemkekifalta.core.ItemDto
import com.kekitemkekifalta.core.MarketDto
import com.kekitemkekifalta.core.ShelfNoteDto
import com.kekitemkekifalta.core.SyncMerge
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.AppDatabase
import com.kekitemkekifalta.data.db.ItemCycleEntity
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.MarketEntity
import com.kekitemkekifalta.data.db.ShelfNoteEntity
import com.kekitemkekifalta.core.TextNormalizer

enum class ImportMode {
    /** Row by row, newest updatedAt wins. Nothing local is lost. */
    MERGE,

    /** Local data is replaced by the backup. */
    REPLACE,
}

data class BackupSummary(val items: Int, val markets: Int, val exportedAt: Long)

interface BackupRepository {
    suspend fun exportJson(): String

    /** Parses and validates; throws BackupFormatException with a pt-BR message. */
    fun inspect(json: String): BackupSummary

    suspend fun import(json: String, mode: ImportMode): BackupSummary
}

class RoomBackupRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) : BackupRepository {

    override suspend fun exportJson(): String {
        val backup = db.withTransaction {
            BackupFile(
                exportedAt = now(),
                householdId = settings.householdId,
                items = db.itemDao().everything().map { it.toDto() },
                cycles = db.cycleDao().everything().map { it.toDto() },
                markets = db.marketDao().everything().map { it.toDto() },
                aisles = db.aisleDao().everything().map { it.toDto() },
                shelfNotes = db.shelfNoteDao().everything().map { it.toDto() },
            )
        }
        return BackupCodec.encode(backup)
    }

    override fun inspect(json: String): BackupSummary = BackupCodec.decode(json).summary()

    override suspend fun import(json: String, mode: ImportMode): BackupSummary {
        val backup = BackupCodec.decode(json)
        db.withTransaction {
            val items = db.itemDao()
            val cycles = db.cycleDao()
            val markets = db.marketDao()
            val aisles = db.aisleDao()
            val notes = db.shelfNoteDao()
            if (mode == ImportMode.REPLACE) {
                items.wipe()
                cycles.wipe()
                markets.wipe()
                aisles.wipe()
                notes.wipe()
            }
            items.upsertAll(SyncMerge.rowsToApply(items.everything(), backup.items.map { it.toEntity() }, { it.id }, { it.updatedAt }))
            cycles.upsertAll(SyncMerge.rowsToApply(cycles.everything(), backup.cycles.map { it.toEntity() }, { it.id }, { it.updatedAt }))
            markets.upsertAll(SyncMerge.rowsToApply(markets.everything(), backup.markets.map { it.toEntity() }, { it.id }, { it.updatedAt }))
            aisles.upsertAll(SyncMerge.rowsToApply(aisles.everything(), backup.aisles.map { it.toEntity() }, { it.id }, { it.updatedAt }))
            notes.upsertAll(SyncMerge.rowsToApply(notes.everything(), backup.shelfNotes.map { it.toEntity() }, { it.id }, { it.updatedAt }))

            // Both phones end up in the same household, ready for a future sync.
            val household = backup.householdId
            items.setHousehold(household)
            cycles.setHousehold(household)
            markets.setHousehold(household)
            aisles.setHousehold(household)
            notes.setHousehold(household)
        }
        settings.setHouseholdId(backup.householdId)
        return backup.summary()
    }

    private fun BackupFile.summary() = BackupSummary(
        items = items.count { it.deletedAt == null },
        markets = markets.count { it.deletedAt == null },
        exportedAt = exportedAt,
    )
}

private fun ItemEntity.toDto() = ItemDto(
    id, householdId, name, sector, quantity, unit, note, clockType, estimatedDays, estimateQuantity,
    learnedCycles, side, stockedAt, snoozedUntil, inCartAt, catalogKey, createdAt, updatedAt, deletedAt,
)

private fun ItemDto.toEntity() = ItemEntity(
    id = id, householdId = householdId, name = name, normalizedName = TextNormalizer.normalize(name),
    sector = sector, quantity = quantity, unit = unit, note = note, clockType = clockType,
    estimatedDays = estimatedDays, estimateQuantity = estimateQuantity, learnedCycles = learnedCycles,
    side = side, stockedAt = stockedAt, snoozedUntil = snoozedUntil, inCartAt = inCartAt,
    catalogKey = catalogKey, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun ItemCycleEntity.toDto() =
    CycleDto(id, householdId, itemId, startedAt, endedAt, endReason, quantity, unit, expectedDays, updatedAt, deletedAt)

private fun CycleDto.toEntity() =
    ItemCycleEntity(id, householdId, itemId, startedAt, endedAt, endReason, quantity, unit, expectedDays, updatedAt, deletedAt)

private fun MarketEntity.toDto() = MarketDto(id, householdId, name, createdAt, updatedAt, deletedAt)
private fun MarketDto.toEntity() = MarketEntity(id, householdId, name, createdAt, updatedAt, deletedAt)

private fun AisleEntity.toDto() = AisleDto(id, householdId, marketId, name, position, sectors, updatedAt, deletedAt)
private fun AisleDto.toEntity() = AisleEntity(id, householdId, marketId, name, position, sectors, updatedAt, deletedAt)

private fun ShelfNoteEntity.toDto() = ShelfNoteDto(id, householdId, itemId, marketId, note, aisleId, updatedAt, deletedAt)
private fun ShelfNoteDto.toEntity() = ShelfNoteEntity(id, householdId, itemId, marketId, note, aisleId, updatedAt, deletedAt)
