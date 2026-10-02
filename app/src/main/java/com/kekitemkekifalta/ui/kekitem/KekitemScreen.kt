package com.kekitemkekifalta.ui.kekitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kekitemkekifalta.AppContainer
import com.kekitemkekifalta.R
import com.kekitemkekifalta.UiMessage
import com.kekitemkekifalta.container
import com.kekitemkekifalta.core.ClockType
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.StockClock
import com.kekitemkekifalta.core.StockState
import com.kekitemkekifalta.core.TextNormalizer
import com.kekitemkekifalta.data.NewItem
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.Side
import com.kekitemkekifalta.data.clock
import com.kekitemkekifalta.data.isHave
import com.kekitemkekifalta.data.quantityLabel
import com.kekitemkekifalta.data.sectorEnum
import com.kekitemkekifalta.data.stockState
import com.kekitemkekifalta.ui.common.applyAddOption
import com.kekitemkekifalta.ui.common.tickerFlow
import com.kekitemkekifalta.ui.components.AddOption
import com.kekitemkekifalta.ui.components.AddOptions
import com.kekitemkekifalta.ui.components.ChoiceChip
import com.kekitemkekifalta.ui.components.EmptyState
import com.kekitemkekifalta.ui.components.FuelBar
import com.kekitemkekifalta.ui.components.KekButton
import com.kekitemkekifalta.ui.components.KekGhostButton
import com.kekitemkekifalta.ui.components.QuickAddBar
import com.kekitemkekifalta.ui.components.ScreenHeader
import com.kekitemkekifalta.ui.components.SectorChip
import com.kekitemkekifalta.ui.components.StatusPill
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.theme.KekTheme
import com.kekitemkekifalta.ui.theme.inkOnLight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Staples for a quick start on an empty house. */
private val BASICS = listOf(
    "Arroz", "Feijão", "Açúcar", "Café", "Óleo", "Sal", "Leite", "Ovos", "Banana", "Tomate", "Cebola", "Alho",
    "Papel higiênico", "Detergente", "Sabonete", "Pasta de dente",
)

enum class HaveSort(val label: String) {
    SOON("acaba primeiro"),
    AZ("A–Z"),
}

data class HaveRow(val item: ItemEntity, val state: StockState?)

data class KekitemUi(
    val query: String = "",
    val sector: Sector? = null,
    val sort: HaveSort = HaveSort.SOON,
    val rows: List<HaveRow> = emptyList(),
    val totalHave: Int = 0,
    val sectorsPresent: List<Sector> = emptyList(),
    val addOptions: List<AddOption> = emptyList(),
    val loaded: Boolean = false,
)

class KekitemViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sector = MutableStateFlow<Sector?>(null)
    private val sort = MutableStateFlow(HaveSort.SOON)

    val ui: StateFlow<KekitemUi> = combine(c.items.observeItems(), query, sector, sort, tickerFlow()) { items, q, s, order, now ->
        val have = items.filter { it.isHave }
        val nq = TextNormalizer.normalize(q)
        val rows = have
            .filter { s == null || it.sectorEnum == s }
            .filter { nq.isEmpty() || it.normalizedName.contains(nq) }
            .map { HaveRow(it, it.stockState(now)) }
        val sorted = when (order) {
            HaveSort.SOON -> rows.sortedWith(compareBy<HaveRow> { it.state?.remainingDays ?: Double.MAX_VALUE }.thenBy { it.item.normalizedName })
            HaveSort.AZ -> rows.sortedBy { it.item.normalizedName }
        }
        KekitemUi(
            query = q,
            sector = s,
            sort = order,
            rows = sorted,
            totalHave = have.size,
            sectorsPresent = have.map { it.sectorEnum }.distinct().sortedBy { it.ordinal },
            addOptions = AddOptions.compute(q, items, Side.HAVE),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KekitemUi())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSector(value: Sector?) {
        sector.value = if (sector.value == value) null else value
    }

    fun toggleSort() {
        sort.value = if (sort.value == HaveSort.SOON) HaveSort.AZ else HaveSort.SOON
    }

    fun add(option: AddOption, sector: Sector?) = viewModelScope.launch {
        c.applyAddOption(option, sector, Side.HAVE)
        query.value = ""
    }

    fun addBasics() = c.appScope.launch {
        BASICS.forEach { c.items.add(NewItem(it), Side.HAVE) }
        c.messages.emit(UiMessage.Text("${BASICS.size} itens básicos no kekitem. Apague o que não tiver."))
    }

    fun finished(id: String) = viewModelScope.launch {
        c.items.markFinished(id)?.let { c.messages.emit(UiMessage.Undo(it)) }
    }

    fun spoiled(id: String) = viewModelScope.launch {
        c.items.markSpoiled(id)?.let { c.messages.emit(UiMessage.Undo(it)) }
    }
}

@Composable
fun KekitemScreen(onOpenItem: (String) -> Unit) {
    val context = LocalContext.current
    val vm: KekitemViewModel = viewModel { KekitemViewModel(context.container) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            ScreenHeader(title = "kekitem", subtitle = "o que tem em casa")
        }
        item(key = "add") {
            QuickAddBar(
                query = ui.query,
                onQueryChange = vm::setQuery,
                options = ui.addOptions,
                placeholder = "Buscar ou adicionar…",
                existingVerb = "está no kekifalta · comprei",
                onPick = { option, sector -> vm.add(option, sector) },
                defaultSector = ui.sector ?: Sector.OUTROS,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (ui.totalHave > 0) {
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(key = "sort") {
                        ChoiceChip(ui.sort.label, selected = true, onClick = vm::toggleSort, leadingIcon = R.drawable.ic_sort)
                    }
                    item(key = "all") {
                        ChoiceChip("Todos", selected = ui.sector == null, onClick = { vm.setSector(null) })
                    }
                    items(ui.sectorsPresent, key = { it.key }) { sector ->
                        SectorChip(sector, selected = ui.sector == sector, onClick = { vm.setSector(sector) })
                    }
                }
            }
        }
        if (ui.loaded && ui.totalHave == 0) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("🫙", "A casa tá vazia?", "Adicione ali em cima o que tem em casa. O app aprende quanto tempo cada coisa dura.")
                    KekGhostButton("Começar com o básico", onClick = { vm.addBasics() }, icon = R.drawable.ic_sparkle)
                }
            }
        } else if (ui.loaded && ui.rows.isEmpty() && ui.query.isEmpty()) {
            item(key = "empty-filter") {
                EmptyState("🔎", "Nada nesse setor", "Troque o filtro ali em cima.")
            }
        }
        items(ui.rows, key = { it.item.id }) { row ->
            HaveCard(
                row = row,
                onOpen = { onOpenItem(row.item.id) },
                onFinished = { vm.finished(row.item.id) },
                onSpoiled = { vm.spoiled(row.item.id) },
                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
            )
        }
    }
}

@Composable
private fun HaveCard(row: HaveRow, onOpen: () -> Unit, onFinished: () -> Unit, onSpoiled: () -> Unit, modifier: Modifier = Modifier) {
    val item = row.item
    val state = row.state
    val muted = KekTheme.colors.muted
    StickerCard(modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.sectorEnum.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildAnnotatedString {
                            append(item.name)
                            item.quantityLabel?.let { qty ->
                                withStyle(SpanStyle(color = muted, fontSize = 13.sp)) { append("  $qty") }
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = KekTheme.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(state.status)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            StockClock.remainingLabel(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FuelBar(state, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                KekButton("acabou", onClick = onFinished, icon = R.drawable.ic_check, color = KekTheme.colors.mustard, contentColor = KekTheme.colors.inkOnLight)
                if (item.clock == ClockType.SPOILS) {
                    Spacer(Modifier.height(6.dp))
                    KekGhostButton("estragou", onClick = onSpoiled, icon = R.drawable.ic_waste)
                }
            }
        }
    }
}
