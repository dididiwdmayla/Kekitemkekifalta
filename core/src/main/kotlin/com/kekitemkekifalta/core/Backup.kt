package com.kekitemkekifalta.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Backup file format. Mirrors the database rows one to one, tombstones (deletedAt) included,
 * so importing works as a manual sync: rows are merged by id, newest updatedAt wins.
 * Only add fields with defaults; never rename or remove (old backups must keep importing).
 */

@Serializable
data class ItemDto(
    val id: String,
    val householdId: String,
    val name: String,
    val sector: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null,
    val clockType: String,
    val estimatedDays: Double,
    val estimateQuantity: Double? = null,
    val learnedCycles: Int = 0,
    val side: String,
    val stockedAt: Long? = null,
    val snoozedUntil: Long? = null,
    val inCartAt: Long? = null,
    val catalogKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class CycleDto(
    val id: String,
    val householdId: String,
    val itemId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val endReason: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val expectedDays: Double,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class MarketDto(
    val id: String,
    val householdId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class AisleDto(
    val id: String,
    val householdId: String,
    val marketId: String,
    val name: String,
    val position: Int,
    val sectors: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class ShelfNoteDto(
    val id: String,
    val householdId: String,
    val itemId: String,
    val marketId: String,
    val note: String = "",
    val aisleId: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val householdId: String,
    val items: List<ItemDto> = emptyList(),
    val cycles: List<CycleDto> = emptyList(),
    val markets: List<MarketDto> = emptyList(),
    val aisles: List<AisleDto> = emptyList(),
    val shelfNotes: List<ShelfNoteDto> = emptyList(),
) {
    companion object {
        const val FORMAT = "kekitemkekifalta-backup"
        const val VERSION = 1
    }
}

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    fun decode(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("Esse arquivo não parece um backup do kekitemkekifalta.", e)
        }
        if (backup.format != BackupFile.FORMAT) {
            throw BackupFormatException("Esse arquivo não parece um backup do kekitemkekifalta.")
        }
        if (backup.version > BackupFile.VERSION) {
            throw BackupFormatException("Backup feito por uma versão mais nova do app. Atualize o app e tente de novo.")
        }
        return backup
    }
}

object SyncMerge {
    /**
     * Last-write-wins merge by id. Returns the incoming rows that should be written locally:
     * rows missing locally, or strictly newer than the local copy.
     */
    fun <T> rowsToApply(
        local: List<T>,
        incoming: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
    ): List<T> {
        val localById = local.associateBy(id)
        return incoming
            .groupBy(id).values.map { dupes -> dupes.maxBy(updatedAt) }
            .filter { row ->
                val current = localById[id(row)]
                current == null || updatedAt(row) > updatedAt(current)
            }
    }
}
