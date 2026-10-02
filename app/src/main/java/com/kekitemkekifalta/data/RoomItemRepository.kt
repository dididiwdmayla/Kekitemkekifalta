package com.kekitemkekifalta.data

import androidx.room.withTransaction
import com.kekitemkekifalta.core.Catalog
import com.kekitemkekifalta.core.DurationEstimator
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.StockClock
import com.kekitemkekifalta.core.TextNormalizer
import com.kekitemkekifalta.data.db.AppDatabase
import com.kekitemkekifalta.data.db.EndReason
import com.kekitemkekifalta.data.db.ItemCycleEntity
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.Side
import com.kekitemkekifalta.data.db.SpoiledRow
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomItemRepository(
    private val db: AppDatabase,
    private val householdId: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) : ItemRepository {
    private val items = db.itemDao()
    private val cycles = db.cycleDao()

    private enum class Learn { FINISHED, SPOILED, NONE }

    override fun observeItems(): Flow<List<ItemEntity>> = items.observeAll()
    override fun observeItem(id: String): Flow<ItemEntity?> = items.observe(id)
    override fun observeCycles(itemId: String): Flow<List<ItemCycleEntity>> = cycles.observeForItem(itemId)
    override fun observeSpoiled(from: Long, to: Long): Flow<List<SpoiledRow>> = cycles.observeSpoiled(from, to)
    override suspend fun allLiveItems(): List<ItemEntity> = items.allLive()

    override suspend fun add(newItem: NewItem, side: String): AddResult = db.withTransaction {
        val t = now()
        val typed = TextNormalizer.prettyName(newItem.name)
        val normalized = TextNormalizer.normalize(typed)
        val existing = items.findByNormalizedName(normalized)
        if (existing != null) {
            if (existing.side == side) return@withTransaction AddResult.AlreadyThere(existing)
            val token = if (side == Side.NEED) {
                endCycle(existing, t, EndReason.FINISHED, Learn.FINISHED, "${existing.name} foi pro kekifalta")
            } else {
                startStock(existing, t, "${existing.name} voltou pro kekitem")
            }
            val moved = items.get(existing.id) ?: existing
            return@withTransaction if (token != null) AddResult.Moved(moved, token) else AddResult.AlreadyThere(moved)
        }

        val catalog = Catalog.find(typed)
        val name = if (catalog != null && catalog.key == normalized) catalog.name else typed
        val sector = newItem.sector ?: catalog?.sector ?: Sector.OUTROS
        val clock = catalog?.clock ?: sector.defaultClock
        val estimate = DurationEstimator.initial(catalog?.days ?: sector.defaultDays, newItem.quantity)
        var item = ItemEntity(
            id = uuid(),
            householdId = householdId(),
            name = name,
            normalizedName = TextNormalizer.normalize(name),
            sector = sector.key,
            quantity = newItem.quantity,
            unit = newItem.unit?.trim()?.ifBlank { null },
            note = null,
            clockType = clock.key,
            estimatedDays = estimate.days,
            estimateQuantity = estimate.referenceQuantity,
            learnedCycles = 0,
            side = side,
            stockedAt = if (side == Side.HAVE) t else null,
            snoozedUntil = null,
            inCartAt = null,
            catalogKey = catalog?.key,
            createdAt = t,
            updatedAt = t,
            deletedAt = null,
        )
        items.upsert(item)
        if (side == Side.HAVE) openCycle(item, t)
        item = items.get(item.id) ?: item
        AddResult.Created(item)
    }

    override suspend fun markFinished(id: String): UndoToken? = db.withTransaction {
        items.get(id)?.let { endCycle(it, now(), EndReason.FINISHED, Learn.FINISHED, "${it.name} acabou: foi pro kekifalta") }
    }

    override suspend fun markSpoiled(id: String): UndoToken? = db.withTransaction {
        items.get(id)?.let { endCycle(it, now(), EndReason.SPOILED, Learn.SPOILED, "${it.name} estragou: foi pro kekifalta") }
    }

    override suspend fun confirmSuggestion(id: String): UndoToken? = db.withTransaction {
        items.get(id)?.let { endCycle(it, now(), EndReason.FINISHED, Learn.NONE, "${it.name} foi pra lista") }
    }

    override suspend fun stillHas(id: String): UndoToken? = db.withTransaction {
        val item = items.get(id) ?: return@withTransaction null
        val stockedAt = item.stockedAt ?: return@withTransaction null
        if (!item.isHave) return@withTransaction null
        val t = now()
        val estimate = DurationEstimator.onStillHas(item.estimate, item.clock, item.quantity, StockClock.elapsedDays(stockedAt, t))
        val expected = DurationEstimator.expectedDays(estimate, item.clock, item.quantity)
        val snoozeMs = (DurationEstimator.stillHasSnoozeDays(expected) * StockClock.DAY_MS).toLong()
        items.upsert(
            item.copy(
                estimatedDays = estimate.days,
                estimateQuantity = estimate.referenceQuantity,
                learnedCycles = estimate.learnedCycles,
                snoozedUntil = t + snoozeMs,
                updatedAt = t,
            ),
        )
        UndoToken("Anotado: ainda tem ${item.name}", listOf(item), emptyList(), emptyList())
    }

    override suspend fun restock(id: String): UndoToken? = db.withTransaction {
        items.get(id)?.let { startStock(it, now(), "${it.name} foi pro kekitem") }
    }

    override suspend fun edit(id: String, edit: ItemEdit): Unit = db.withTransaction {
        val item = items.get(id) ?: return@withTransaction
        val t = now()
        val name = TextNormalizer.prettyName(edit.name).ifBlank { item.name }
        val unit = edit.unit?.trim()?.ifBlank { null }
        val unitChanged = TextNormalizer.normalize(unit.orEmpty()) != TextNormalizer.normalize(item.unit.orEmpty())
        val reference = when {
            edit.quantity == null -> item.estimateQuantity
            item.estimateQuantity == null || unitChanged -> edit.quantity
            else -> item.estimateQuantity
        }
        val updated = item.copy(
            name = name,
            normalizedName = TextNormalizer.normalize(name),
            sector = edit.sector.key,
            clockType = edit.clock.key,
            quantity = edit.quantity,
            unit = unit,
            note = edit.note?.trim()?.ifBlank { null },
            estimateQuantity = reference,
            updatedAt = t,
        )
        items.upsert(updated)
        if (updated.isHave && (updated.quantity != item.quantity || updated.unit != item.unit)) {
            cycles.openCycles(id).forEach { cycles.upsert(it.copy(quantity = updated.quantity, unit = updated.unit, updatedAt = t)) }
        }
    }

    override suspend fun setDuration(id: String, days: Double): Unit = db.withTransaction {
        val item = items.get(id) ?: return@withTransaction
        val estimate = DurationEstimator.manual(item.estimate, days, item.quantity)
        items.upsert(
            item.copy(
                estimatedDays = estimate.days,
                estimateQuantity = estimate.referenceQuantity,
                learnedCycles = estimate.learnedCycles,
                snoozedUntil = null,
                updatedAt = now(),
            ),
        )
    }

    override suspend fun delete(id: String): UndoToken? = db.withTransaction {
        val item = items.get(id) ?: return@withTransaction null
        val t = now()
        items.upsert(item.copy(deletedAt = t, inCartAt = null, updatedAt = t))
        UndoToken("${item.name} foi excluído", listOf(item), emptyList(), emptyList())
    }

    override suspend fun undo(token: UndoToken): Unit = db.withTransaction {
        val t = now()
        token.items.forEach { items.upsert(it.copy(updatedAt = t)) }
        token.cycles.forEach { cycles.upsert(it.copy(updatedAt = t)) }
        token.createdCycleIds.forEach { id ->
            cycles.get(id)?.let { cycles.upsert(it.copy(deletedAt = t, updatedAt = t)) }
        }
    }

    override suspend fun setInCart(id: String, inCart: Boolean): Unit = db.withTransaction {
        val item = items.get(id) ?: return@withTransaction
        val t = now()
        items.upsert(item.copy(inCartAt = if (inCart) t else null, updatedAt = t))
    }

    override suspend fun cartInCheckOrder(): List<ItemEntity> = items.inCart()

    override suspend fun completeShopping(): Int = db.withTransaction {
        val t = now()
        val cart = items.inCart()
        cart.forEach { item ->
            if (item.isNeed) startStock(item, t, "") else items.upsert(item.copy(inCartAt = null, updatedAt = t))
        }
        cart.size
    }

    override suspend fun clearCart(): Unit = db.withTransaction {
        val t = now()
        items.inCart().forEach { items.upsert(it.copy(inCartAt = null, updatedAt = t)) }
    }

    /** HAVE -> NEED, closing the open cycle and (maybe) learning from it. */
    private suspend fun endCycle(item: ItemEntity, t: Long, reason: String, learn: Learn, label: String): UndoToken? {
        if (!item.isHave) return null
        val open = cycles.openCycles(item.id)
        val created = mutableListOf<String>()
        val elapsed = item.stockedAt?.let { StockClock.elapsedDays(it, t) }
        val estimate = when {
            elapsed == null -> item.estimate
            learn == Learn.FINISHED -> DurationEstimator.onFinished(item.estimate, item.clock, item.quantity, elapsed)
            learn == Learn.SPOILED -> DurationEstimator.onSpoiled(item.estimate, item.clock, item.quantity, elapsed)
            else -> item.estimate
        }
        open.forEach { cycles.upsert(it.copy(endedAt = t, endReason = reason, updatedAt = t)) }
        if (open.isEmpty() && item.stockedAt != null) {
            // History must stay complete (the waste screen reads it) even if the open row went missing.
            val id = uuid()
            cycles.upsert(
                ItemCycleEntity(id, householdId(), item.id, item.stockedAt, t, reason, item.quantity, item.unit, item.expectedDays, t, null),
            )
            created += id
        }
        items.upsert(
            item.copy(
                side = Side.NEED,
                stockedAt = null,
                snoozedUntil = null,
                inCartAt = null,
                estimatedDays = estimate.days,
                estimateQuantity = estimate.referenceQuantity,
                learnedCycles = estimate.learnedCycles,
                updatedAt = t,
            ),
        )
        return UndoToken(label, listOf(item), open, created)
    }

    /** NEED -> HAVE, opening a new cycle. */
    private suspend fun startStock(item: ItemEntity, t: Long, label: String): UndoToken? {
        if (item.isHave) return null
        val updated = item.copy(side = Side.HAVE, stockedAt = t, snoozedUntil = null, inCartAt = null, updatedAt = t)
        items.upsert(updated)
        val cycle = openCycle(updated, t)
        return UndoToken(label, listOf(item), emptyList(), listOf(cycle.id))
    }

    private suspend fun openCycle(item: ItemEntity, t: Long): ItemCycleEntity {
        val cycle = ItemCycleEntity(uuid(), householdId(), item.id, t, null, null, item.quantity, item.unit, item.expectedDays, t, null)
        cycles.upsert(cycle)
        return cycle
    }

    private fun uuid() = UUID.randomUUID().toString()
}
