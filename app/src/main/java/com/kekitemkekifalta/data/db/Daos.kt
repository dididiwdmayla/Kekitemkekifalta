package com.kekitemkekifalta.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY normalizedName")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE deletedAt IS NULL")
    suspend fun allLive(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): ItemEntity?

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND inCartAt IS NOT NULL ORDER BY inCartAt")
    suspend fun inCart(): List<ItemEntity>

    @Query("SELECT * FROM items")
    suspend fun everything(): List<ItemEntity>

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("UPDATE items SET householdId = :householdId")
    suspend fun setHousehold(householdId: String)

    @Query("DELETE FROM items")
    suspend fun wipe()
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM item_cycles WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeForItem(itemId: String): Flow<List<ItemCycleEntity>>

    @Query("SELECT * FROM item_cycles WHERE itemId = :itemId AND endedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC")
    suspend fun openCycles(itemId: String): List<ItemCycleEntity>

    @Query(
        """
        SELECT c.id AS cycleId, c.itemId AS itemId, i.name AS itemName, i.sector AS sector,
               c.startedAt AS startedAt, c.endedAt AS endedAt, c.quantity AS quantity, c.unit AS unit
        FROM item_cycles c INNER JOIN items i ON i.id = c.itemId
        WHERE c.deletedAt IS NULL AND c.endReason = 'spoiled' AND c.endedAt >= :from AND c.endedAt < :to
        ORDER BY c.endedAt DESC
        """,
    )
    fun observeSpoiled(from: Long, to: Long): Flow<List<SpoiledRow>>

    @Query("SELECT * FROM item_cycles WHERE id = :id")
    suspend fun get(id: String): ItemCycleEntity?

    @Query("SELECT * FROM item_cycles")
    suspend fun everything(): List<ItemCycleEntity>

    @Upsert
    suspend fun upsert(cycle: ItemCycleEntity)

    @Upsert
    suspend fun upsertAll(cycles: List<ItemCycleEntity>)

    @Query("UPDATE item_cycles SET householdId = :householdId")
    suspend fun setHousehold(householdId: String)

    @Query("DELETE FROM item_cycles")
    suspend fun wipe()
}

@Dao
interface MarketDao {
    @Query("SELECT * FROM markets WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeAll(): Flow<List<MarketEntity>>

    @Query("SELECT * FROM markets WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<MarketEntity?>

    @Query("SELECT * FROM markets WHERE id = :id")
    suspend fun get(id: String): MarketEntity?

    @Query("SELECT * FROM markets")
    suspend fun everything(): List<MarketEntity>

    @Upsert
    suspend fun upsert(market: MarketEntity)

    @Upsert
    suspend fun upsertAll(markets: List<MarketEntity>)

    @Query("UPDATE markets SET householdId = :householdId")
    suspend fun setHousehold(householdId: String)

    @Query("DELETE FROM markets")
    suspend fun wipe()
}

@Dao
interface AisleDao {
    @Query("SELECT * FROM aisles WHERE marketId = :marketId AND deletedAt IS NULL ORDER BY position, name")
    fun observeForMarket(marketId: String): Flow<List<AisleEntity>>

    @Query("SELECT * FROM aisles WHERE marketId = :marketId AND deletedAt IS NULL ORDER BY position, name")
    suspend fun forMarket(marketId: String): List<AisleEntity>

    @Query("SELECT * FROM aisles WHERE deletedAt IS NULL")
    fun observeAllLive(): Flow<List<AisleEntity>>

    @Query("SELECT * FROM aisles WHERE id = :id")
    suspend fun get(id: String): AisleEntity?

    @Query("SELECT * FROM aisles")
    suspend fun everything(): List<AisleEntity>

    @Upsert
    suspend fun upsert(aisle: AisleEntity)

    @Upsert
    suspend fun upsertAll(aisles: List<AisleEntity>)

    @Query("UPDATE aisles SET householdId = :householdId")
    suspend fun setHousehold(householdId: String)

    @Query("DELETE FROM aisles")
    suspend fun wipe()
}

@Dao
interface ShelfNoteDao {
    @Query("SELECT * FROM shelf_notes WHERE marketId = :marketId AND deletedAt IS NULL")
    fun observeForMarket(marketId: String): Flow<List<ShelfNoteEntity>>

    @Query("SELECT * FROM shelf_notes WHERE itemId = :itemId AND deletedAt IS NULL")
    fun observeForItem(itemId: String): Flow<List<ShelfNoteEntity>>

    @Query("SELECT * FROM shelf_notes WHERE itemId = :itemId AND marketId = :marketId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun find(itemId: String, marketId: String): ShelfNoteEntity?

    @Query("SELECT * FROM shelf_notes WHERE itemId = :itemId AND deletedAt IS NULL")
    suspend fun forItem(itemId: String): List<ShelfNoteEntity>

    @Query("SELECT * FROM shelf_notes WHERE marketId = :marketId AND deletedAt IS NULL")
    suspend fun forMarket(marketId: String): List<ShelfNoteEntity>

    @Query("SELECT * FROM shelf_notes")
    suspend fun everything(): List<ShelfNoteEntity>

    @Upsert
    suspend fun upsert(note: ShelfNoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<ShelfNoteEntity>)

    @Query("UPDATE shelf_notes SET householdId = :householdId")
    suspend fun setHousehold(householdId: String)

    @Query("DELETE FROM shelf_notes")
    suspend fun wipe()
}
