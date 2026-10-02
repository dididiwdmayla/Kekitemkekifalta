package com.kekitemkekifalta.data

import androidx.room.withTransaction
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.AppDatabase
import com.kekitemkekifalta.data.db.MarketEntity
import com.kekitemkekifalta.data.db.ShelfNoteEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MarketRepository {
    fun observeMarkets(): Flow<List<MarketEntity>>
    fun observeMarket(id: String): Flow<MarketEntity?>
    fun observeAisles(marketId: String): Flow<List<AisleEntity>>
    fun observeShelfNotes(marketId: String): Flow<List<ShelfNoteEntity>>
    fun observeShelfNotesForItem(itemId: String): Flow<List<ShelfNoteEntity>>

    /** Creates a market with the default aisle template. Returns its id. */
    suspend fun createMarket(name: String): String
    suspend fun renameMarket(id: String, name: String)
    suspend fun deleteMarket(id: String)

    suspend fun addAisle(marketId: String, name: String): String
    suspend fun renameAisle(aisleId: String, name: String)

    /** Sets the aisle's sectors. A sector lives in one aisle per market, so it leaves the others. */
    suspend fun setAisleSectors(aisleId: String, sectors: List<Sector>)
    suspend fun deleteAisle(aisleId: String)
    suspend fun reorderAisles(marketId: String, orderedIds: List<String>)

    /** Blank note and null aisle remove the entry. */
    suspend fun setShelfNote(itemId: String, marketId: String, note: String, aisleId: String?)
}

class RoomMarketRepository(
    private val db: AppDatabase,
    private val householdId: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) : MarketRepository {
    private val markets = db.marketDao()
    private val aisles = db.aisleDao()
    private val notes = db.shelfNoteDao()

    override fun observeMarkets(): Flow<List<MarketEntity>> = markets.observeAll()
    override fun observeMarket(id: String): Flow<MarketEntity?> = markets.observe(id)
    override fun observeAisles(marketId: String): Flow<List<AisleEntity>> = aisles.observeForMarket(marketId)
    override fun observeShelfNotes(marketId: String): Flow<List<ShelfNoteEntity>> = notes.observeForMarket(marketId)
    override fun observeShelfNotesForItem(itemId: String): Flow<List<ShelfNoteEntity>> = notes.observeForItem(itemId)

    override suspend fun createMarket(name: String): String = db.withTransaction {
        val t = now()
        val market = MarketEntity(uuid(), householdId(), name.trim().ifBlank { "Mercado" }, t, t, null)
        markets.upsert(market)
        aisles.upsertAll(
            DEFAULT_TEMPLATE.mapIndexed { index, sector ->
                AisleEntity(uuid(), householdId(), market.id, sector.label, index, Sector.encodeList(listOf(sector)), t, null)
            },
        )
        market.id
    }

    override suspend fun renameMarket(id: String, name: String): Unit = db.withTransaction {
        val market = markets.get(id) ?: return@withTransaction
        val clean = name.trim()
        if (clean.isNotEmpty()) markets.upsert(market.copy(name = clean, updatedAt = now()))
    }

    override suspend fun deleteMarket(id: String): Unit = db.withTransaction {
        val market = markets.get(id) ?: return@withTransaction
        val t = now()
        markets.upsert(market.copy(deletedAt = t, updatedAt = t))
        aisles.forMarket(id).forEach { aisles.upsert(it.copy(deletedAt = t, updatedAt = t)) }
        notes.forMarket(id).forEach { notes.upsert(it.copy(deletedAt = t, updatedAt = t)) }
    }

    override suspend fun addAisle(marketId: String, name: String): String = db.withTransaction {
        val t = now()
        val position = (aisles.forMarket(marketId).maxOfOrNull { it.position } ?: -1) + 1
        val aisle = AisleEntity(uuid(), householdId(), marketId, name.trim().ifBlank { "Corredor ${position + 1}" }, position, "", t, null)
        aisles.upsert(aisle)
        aisle.id
    }

    override suspend fun renameAisle(aisleId: String, name: String): Unit = db.withTransaction {
        val aisle = aisles.get(aisleId) ?: return@withTransaction
        val clean = name.trim()
        if (clean.isNotEmpty()) aisles.upsert(aisle.copy(name = clean, updatedAt = now()))
    }

    override suspend fun setAisleSectors(aisleId: String, sectors: List<Sector>): Unit = db.withTransaction {
        val aisle = aisles.get(aisleId) ?: return@withTransaction
        val t = now()
        aisles.forMarket(aisle.marketId).filter { it.id != aisleId }.forEach { other ->
            val current = Sector.decodeList(other.sectors)
            val remaining = current - sectors.toSet()
            if (remaining.size != current.size) aisles.upsert(other.copy(sectors = Sector.encodeList(remaining), updatedAt = t))
        }
        aisles.upsert(aisle.copy(sectors = Sector.encodeList(sectors), updatedAt = t))
    }

    override suspend fun deleteAisle(aisleId: String): Unit = db.withTransaction {
        val aisle = aisles.get(aisleId) ?: return@withTransaction
        val t = now()
        aisles.upsert(aisle.copy(deletedAt = t, updatedAt = t))
        notes.forMarket(aisle.marketId).filter { it.aisleId == aisleId }.forEach {
            notes.upsert(it.copy(aisleId = null, updatedAt = t))
        }
    }

    override suspend fun reorderAisles(marketId: String, orderedIds: List<String>): Unit = db.withTransaction {
        val t = now()
        val byId = aisles.forMarket(marketId).associateBy { it.id }
        val ordered = orderedIds.mapNotNull { byId[it] } + byId.values.filter { it.id !in orderedIds }.sortedBy { it.position }
        ordered.forEachIndexed { index, aisle ->
            if (aisle.position != index) aisles.upsert(aisle.copy(position = index, updatedAt = t))
        }
    }

    override suspend fun setShelfNote(itemId: String, marketId: String, note: String, aisleId: String?): Unit = db.withTransaction {
        val t = now()
        val clean = note.trim()
        val existing = notes.find(itemId, marketId)
        val empty = clean.isEmpty() && aisleId == null
        when {
            existing == null && empty -> Unit
            existing == null -> notes.upsert(ShelfNoteEntity(uuid(), householdId(), itemId, marketId, clean, aisleId, t, null))
            empty && existing.deletedAt == null -> notes.upsert(existing.copy(deletedAt = t, updatedAt = t))
            empty -> Unit
            else -> notes.upsert(existing.copy(note = clean, aisleId = aisleId, deletedAt = null, updatedAt = t))
        }
    }

    private fun uuid() = UUID.randomUUID().toString()

    companion object {
        /** Template for a new market; one aisle per sector, in the usual walking order. */
        val DEFAULT_TEMPLATE: List<Sector> = listOf(
            Sector.HORTIFRUTI,
            Sector.PADARIA,
            Sector.ACOUGUE_FRIOS,
            Sector.LATICINIOS,
            Sector.MERCEARIA,
            Sector.BEBIDAS,
            Sector.CONGELADOS,
            Sector.LIMPEZA,
            Sector.HIGIENE,
            Sector.PET,
            Sector.OUTROS,
        )
    }
}
