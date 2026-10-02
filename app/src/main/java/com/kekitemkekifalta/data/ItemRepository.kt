package com.kekitemkekifalta.data

import com.kekitemkekifalta.core.ClockType
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.data.db.ItemCycleEntity
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.SpoiledRow
import kotlinx.coroutines.flow.Flow

/** What the user typed in quick add. Sector/clock/days fall back to catalog, then sector defaults. */
data class NewItem(
    val name: String,
    val sector: Sector? = null,
    val quantity: Double? = null,
    val unit: String? = null,
)

data class ItemEdit(
    val name: String,
    val sector: Sector,
    val clock: ClockType,
    val quantity: Double?,
    val unit: String?,
    val note: String?,
)

/** Snapshot taken before a change, so a snackbar can undo it. */
data class UndoToken(
    val label: String,
    val items: List<ItemEntity>,
    val cycles: List<ItemCycleEntity>,
    val createdCycleIds: List<String>,
)

sealed interface AddResult {
    val item: ItemEntity

    data class Created(override val item: ItemEntity) : AddResult
    data class Moved(override val item: ItemEntity, val undo: UndoToken) : AddResult
    data class AlreadyThere(override val item: ItemEntity) : AddResult
}

interface ItemRepository {
    fun observeItems(): Flow<List<ItemEntity>>
    fun observeItem(id: String): Flow<ItemEntity?>
    fun observeCycles(itemId: String): Flow<List<ItemCycleEntity>>
    fun observeSpoiled(from: Long, to: Long): Flow<List<SpoiledRow>>
    suspend fun allLiveItems(): List<ItemEntity>

    /** Adds to [side] (Side.HAVE/NEED). An existing item with the same name is moved instead. */
    suspend fun add(newItem: NewItem, side: String): AddResult

    /** kekitem -> kekifalta: "acabou". Learns from the cycle. */
    suspend fun markFinished(id: String): UndoToken?

    /** kekitem -> kekifalta: "estragou". Logs waste and lowers the estimate. */
    suspend fun markSpoiled(id: String): UndoToken?

    /** Suggested item confirmed as gone. Does not change the estimate (the real end is unknown). */
    suspend fun confirmSuggestion(id: String): UndoToken?

    /** Suggested item is still there: raises the estimate and snoozes the suggestion. */
    suspend fun stillHas(id: String): UndoToken?

    /** kekifalta -> kekitem: bought it. Starts a new cycle. */
    suspend fun restock(id: String): UndoToken?

    suspend fun edit(id: String, edit: ItemEdit)
    suspend fun setDuration(id: String, days: Double)
    suspend fun delete(id: String): UndoToken?
    suspend fun undo(token: UndoToken)

    suspend fun setInCart(id: String, inCart: Boolean)

    /** Items currently checked in market mode, in the order they were checked. */
    suspend fun cartInCheckOrder(): List<ItemEntity>

    /** Checked items go home with a new cycle; returns how many. */
    suspend fun completeShopping(): Int

    suspend fun clearCart()
}
