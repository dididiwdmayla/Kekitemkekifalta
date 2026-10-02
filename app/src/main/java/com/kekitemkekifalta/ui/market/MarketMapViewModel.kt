package com.kekitemkekifalta.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kekitemkekifalta.AppContainer
import com.kekitemkekifalta.core.AisleSpec
import com.kekitemkekifalta.core.RouteGrouping
import com.kekitemkekifalta.core.RouteLearning
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.MarketEntity
import com.kekitemkekifalta.data.db.ShelfNoteEntity
import com.kekitemkekifalta.data.isNeed
import com.kekitemkekifalta.data.sectorEnum
import com.kekitemkekifalta.data.toSpec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val LOOSE_GROUP_KEY = "__sem_corredor__"

data class MapItem(val item: ItemEntity, val shelfNote: String?, val checked: Boolean)

data class MapGroup(val aisle: AisleSpec?, val position: Int?, val items: List<MapItem>) {
    val key: String get() = aisle?.id ?: LOOSE_GROUP_KEY
    val pending: Int get() = items.count { !it.checked }
}

data class MarketMapUi(
    val market: MarketEntity? = null,
    val markets: List<MarketEntity> = emptyList(),
    val aisles: List<AisleEntity> = emptyList(),
    val groups: List<MapGroup> = emptyList(),
    val notes: Map<String, ShelfNoteEntity> = emptyMap(),
    val total: Int = 0,
    val checked: Int = 0,
    val currentKey: String? = null,
    val loaded: Boolean = false,
)

data class FinishResult(val bought: Int, val proposedRoute: List<AisleEntity>?)

class MarketMapViewModel(private val c: AppContainer, val marketId: String) : ViewModel() {

    val ui: StateFlow<MarketMapUi> = combine(
        c.markets.observeMarket(marketId),
        c.markets.observeAisles(marketId),
        c.markets.observeShelfNotes(marketId),
        c.items.observeItems(),
        c.markets.observeMarkets(),
    ) { market, aisles, notes, items, markets ->
        val need = items.filter { it.isNeed }
        val notesByItem = notes.associateBy { it.itemId }
        val positions = aisles.withIndex().associate { it.value.id to it.index + 1 }
        val groups = group(aisles, need, notesByItem).map { g ->
            MapGroup(
                aisle = g.aisle,
                position = g.aisle?.let { positions[it.id] },
                items = g.items.map { MapItem(it, notesByItem[it.id]?.note?.takeIf { n -> n.isNotBlank() }, it.inCartAt != null) },
            )
        }
        MarketMapUi(
            market = market,
            markets = markets,
            aisles = aisles,
            groups = groups,
            notes = notesByItem,
            total = need.size,
            checked = need.count { it.inCartAt != null },
            currentKey = groups.firstOrNull { it.pending > 0 }?.key,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketMapUi())

    private fun group(aisles: List<AisleEntity>, items: List<ItemEntity>, notes: Map<String, ShelfNoteEntity>) =
        RouteGrouping.group(
            aisles.map { it.toSpec() },
            items,
            sectorOf = { it.sectorEnum },
            overrideAisleOf = { notes[it.id]?.aisleId },
            sortKey = { it.normalizedName },
        )

    fun toggle(itemId: String, checked: Boolean) = viewModelScope.launch {
        c.items.setInCart(itemId, checked)
    }

    fun setShelfNote(itemId: String, note: String, aisleId: String?) = c.appScope.launch {
        c.markets.setShelfNote(itemId, marketId, note, aisleId)
    }

    fun rememberMarket() = c.settings.setLastMarketId(marketId)

    /** Checked items go home; also compares the picking order with the route (never applied here). */
    suspend fun finish(): FinishResult {
        val cart = c.items.cartInCheckOrder()
        val aisles = c.markets.observeAisles(marketId).first()
        val notes = c.markets.observeShelfNotes(marketId).first().associateBy { it.itemId }
        val aisleOfItem = group(aisles, cart, notes)
            .flatMap { g -> g.aisle?.let { aisle -> g.items.map { it.id to aisle.id } }.orEmpty() }
            .toMap()
        val proposal = RouteLearning.proposeRoute(aisles.map { it.id }, cart.mapNotNull { aisleOfItem[it.id] })
        val bought = c.items.completeShopping()
        val byId = aisles.associateBy { it.id }
        return FinishResult(bought, proposal?.mapNotNull { byId[it] })
    }

    fun applyRoute(order: List<AisleEntity>) = c.appScope.launch {
        c.markets.reorderAisles(marketId, order.map { it.id })
    }
}
