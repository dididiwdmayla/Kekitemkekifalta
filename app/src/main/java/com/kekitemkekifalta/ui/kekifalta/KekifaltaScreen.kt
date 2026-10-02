@file:OptIn(ExperimentalMaterial3Api::class)

package com.kekitemkekifalta.ui.kekifalta

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kekitemkekifalta.AppContainer
import com.kekitemkekifalta.R
import com.kekitemkekifalta.UiMessage
import com.kekitemkekifalta.container
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.StockClock
import com.kekitemkekifalta.core.StockState
import com.kekitemkekifalta.core.StockStatus
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.MarketEntity
import com.kekitemkekifalta.data.db.Side
import com.kekitemkekifalta.data.isHave
import com.kekitemkekifalta.data.isNeed
import com.kekitemkekifalta.data.quantityLabel
import com.kekitemkekifalta.data.sectorEnum
import com.kekitemkekifalta.data.stockState
import com.kekitemkekifalta.export.Exporter
import com.kekitemkekifalta.export.buildExportList
import com.kekitemkekifalta.ui.common.applyAddOption
import com.kekitemkekifalta.ui.common.tickerFlow
import com.kekitemkekifalta.ui.components.AddOption
import com.kekitemkekifalta.ui.components.AddOptions
import com.kekitemkekifalta.ui.components.ChoiceChip
import com.kekitemkekifalta.ui.components.EmptyState
import com.kekitemkekifalta.ui.components.IconCircleButton
import com.kekitemkekifalta.ui.components.KekButton
import com.kekitemkekifalta.ui.components.KekGhostButton
import com.kekitemkekifalta.ui.components.KekTextField
import com.kekitemkekifalta.ui.components.Pill
import com.kekitemkekifalta.ui.components.QuickAddBar
import com.kekitemkekifalta.ui.components.ScreenHeader
import com.kekitemkekifalta.ui.components.SectionTitle
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.theme.KekTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SuggestionRow(val item: ItemEntity, val state: StockState)

data class KekifaltaUi(
    val query: String = "",
    val suggestions: List<SuggestionRow> = emptyList(),
    val groups: List<Pair<Sector, List<ItemEntity>>> = emptyList(),
    val needCount: Int = 0,
    val inCart: Int = 0,
    val addOptions: List<AddOption> = emptyList(),
    val markets: List<MarketEntity> = emptyList(),
    val lastMarketId: String? = null,
    val loaded: Boolean = false,
)

class KekifaltaViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")

    val ui: StateFlow<KekifaltaUi> = combine(
        c.items.observeItems(),
        c.markets.observeMarkets(),
        query,
        c.settings.settings,
        tickerFlow(),
    ) { items, markets, q, settings, now ->
        val need = items.filter { it.isNeed }
        val suggestions = items.filter { it.isHave }
            .mapNotNull { item -> item.stockState(now)?.takeIf { it.status == StockStatus.PROBABLY_OUT }?.let { SuggestionRow(item, it) } }
            .sortedBy { it.state.remainingDays }
        KekifaltaUi(
            query = q,
            suggestions = suggestions,
            groups = need.groupBy { it.sectorEnum }.toList().sortedBy { it.first.ordinal }
                .map { (sector, list) -> sector to list.sortedBy { it.normalizedName } },
            needCount = need.size,
            inCart = need.count { it.inCartAt != null },
            addOptions = AddOptions.compute(q, items, Side.NEED),
            markets = markets,
            lastMarketId = settings.lastMarketId,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KekifaltaUi())

    fun setQuery(value: String) {
        query.value = value
    }

    fun add(option: AddOption, sector: Sector?) = viewModelScope.launch {
        c.applyAddOption(option, sector, Side.NEED)
        query.value = ""
    }

    fun confirm(id: String) = viewModelScope.launch {
        c.items.confirmSuggestion(id)?.let { c.messages.emit(UiMessage.Undo(it)) }
    }

    fun stillHas(id: String) = viewModelScope.launch {
        c.items.stillHas(id)?.let { c.messages.emit(UiMessage.Undo(it)) }
    }

    fun bought(id: String) = viewModelScope.launch {
        c.items.restock(id)?.let { c.messages.emit(UiMessage.Undo(it)) }
    }

    fun rememberMarket(id: String) = c.settings.setLastMarketId(id)

    suspend fun createMarket(name: String): String = c.markets.createMarket(name).also { c.settings.setLastMarketId(it) }

    fun export(context: Context, marketId: String?, action: ExportAction) = viewModelScope.launch {
        try {
            val list = c.buildExportList(marketId)
            when (action) {
                ExportAction.SHARE_TEXT -> Exporter.shareText(context, list.text)
                ExportAction.COPY_TEXT -> {
                    Exporter.copy(context, list.text)
                    c.messages.emit(UiMessage.Text("Lista copiada"))
                }
                ExportAction.SHARE_PDF -> {
                    val file = withContext(Dispatchers.IO) { Exporter.writePdf(context, list) }
                    Exporter.sharePdf(context, file)
                }
                ExportAction.PRINT_PDF -> {
                    val file = withContext(Dispatchers.IO) { Exporter.writePdf(context, list) }
                    Exporter.printPdf(context, file)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            c.messages.emit(UiMessage.Text("Não deu pra exportar: ${e.message ?: e.javaClass.simpleName}"))
        }
    }
}

enum class ExportAction { SHARE_TEXT, COPY_TEXT, SHARE_PDF, PRINT_PDF }

@Composable
fun KekifaltaScreen(onOpenItem: (String) -> Unit, onGoShopping: (String) -> Unit) {
    val context = LocalContext.current
    val vm: KekifaltaViewModel = viewModel { KekifaltaViewModel(context.container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pickMarket by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun goShopping() {
        when {
            ui.markets.size == 1 -> {
                vm.rememberMarket(ui.markets.first().id)
                onGoShopping(ui.markets.first().id)
            }
            else -> pickMarket = true
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            ScreenHeader(title = "kekifalta", subtitle = "o que precisa comprar") {
                IconCircleButton(R.drawable.ic_share, "Exportar lista", { showExport = true })
            }
        }
        item(key = "add") {
            QuickAddBar(
                query = ui.query,
                onQueryChange = vm::setQuery,
                options = ui.addOptions,
                placeholder = "O que falta?",
                existingVerb = "está no kekitem · acabou",
                onPick = { option, sector -> vm.add(option, sector) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (ui.needCount > 0) {
            item(key = "shop") {
                KekButton(
                    text = if (ui.inCart > 0) "Continuar compra (${ui.inCart} no carrinho)" else "Ir ao mercado",
                    onClick = { goShopping() },
                    icon = R.drawable.ic_cart,
                    big = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
        if (ui.suggestions.isNotEmpty()) {
            item(key = "sugg-title") {
                SectionTitle("Sugeridos", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), count = ui.suggestions.size)
            }
            item(key = "sugg-hint") {
                Text(
                    "Pelas contas do app, isso provavelmente acabou.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KekTheme.colors.muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(ui.suggestions, key = { "s-" + it.item.id }) { row ->
                SuggestionCard(
                    row,
                    onConfirm = { vm.confirm(row.item.id) },
                    onStillHas = { vm.stillHas(row.item.id) },
                    onOpen = { onOpenItem(row.item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                )
            }
        }
        item(key = "list-title") {
            SectionTitle("Lista", Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), count = ui.needCount)
        }
        if (ui.loaded && ui.needCount == 0) {
            item(key = "empty") {
                EmptyState("🎉", "Não falta nada!", "Milagre doméstico. Quando algo acabar, marque no kekitem ou adicione aqui.")
            }
        }
        ui.groups.forEach { (sector, list) ->
            item(key = "g-" + sector.key) {
                Text(
                    "${sector.emoji}  ${sector.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = KekTheme.colors.muted,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp).animateItem(),
                )
            }
            items(list, key = { "n-" + it.id }) { item ->
                NeedCard(
                    item,
                    onBought = { vm.bought(item.id) },
                    onOpen = { onOpenItem(item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                )
            }
        }
    }

    if (showExport) {
        ExportSheet(
            markets = ui.markets,
            initialMarketId = ui.lastMarketId,
            onDismiss = { showExport = false },
            onAction = { marketId, action -> vm.export(context, marketId, action) },
        )
    }
    if (pickMarket) {
        MarketPickerDialog(
            markets = ui.markets,
            onDismiss = { pickMarket = false },
            onPick = { id ->
                pickMarket = false
                vm.rememberMarket(id)
                onGoShopping(id)
            },
            onCreate = { name ->
                pickMarket = false
                scope.launch { onGoShopping(vm.createMarket(name)) }
            },
        )
    }
}

@Composable
private fun SuggestionCard(row: SuggestionRow, onConfirm: () -> Unit, onStillHas: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    StickerCard(modifier.fillMaxWidth(), color = KekTheme.colors.tomatoSoft, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.item.sectorEnum.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.item.name, style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(StockClock.remainingLabel(row.state), style = MaterialTheme.typography.bodySmall, color = KekTheme.colors.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KekButton("confirmar", onConfirm, Modifier.weight(1f), icon = R.drawable.ic_cart)
            KekGhostButton("ainda tem", onStillHas, Modifier.weight(1f), icon = R.drawable.ic_home_check)
        }
    }
}

@Composable
private fun NeedCard(item: ItemEntity, onBought: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    StickerCard(modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(item.quantityLabel, item.note).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = KekTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.inCartAt != null) {
                Pill("no carrinho", KekTheme.colors.leafSoft)
                Spacer(Modifier.width(8.dp))
            }
            KekButton("comprei", onBought, icon = R.drawable.ic_check, color = KekTheme.colors.leaf)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportSheet(
    markets: List<MarketEntity>,
    initialMarketId: String?,
    onDismiss: () -> Unit,
    onAction: (String?, ExportAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var marketId by remember { mutableStateOf(initialMarketId?.takeIf { id -> markets.any { it.id == id } }) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = KekTheme.colors.paper) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Exportar o kekifalta", style = MaterialTheme.typography.headlineSmall, color = KekTheme.colors.ink)
            Text("Organizar por", style = MaterialTheme.typography.labelLarge, color = KekTheme.colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Setor", selected = marketId == null, onClick = { marketId = null }, leading = "🗂️")
                markets.forEach { market ->
                    ChoiceChip(market.name, selected = marketId == market.id, onClick = { marketId = market.id }, leading = "🛒")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KekButton("Mandar", { onAction(marketId, ExportAction.SHARE_TEXT) }, Modifier.weight(1f), icon = R.drawable.ic_share)
                KekGhostButton("Copiar", { onAction(marketId, ExportAction.COPY_TEXT) }, Modifier.weight(1f), icon = R.drawable.ic_copy)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KekGhostButton("PDF", { onAction(marketId, ExportAction.SHARE_PDF) }, Modifier.weight(1f), icon = R.drawable.ic_pdf)
                KekGhostButton("Imprimir", { onAction(marketId, ExportAction.PRINT_PDF) }, Modifier.weight(1f), icon = R.drawable.ic_print)
            }
        }
    }
}

@Composable
fun MarketPickerDialog(
    markets: List<MarketEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KekTheme.colors.paper,
        title = { Text(if (markets.isEmpty()) "Primeiro, um mercado" else "Qual mercado?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                markets.forEach { market ->
                    StickerCard(Modifier.fillMaxWidth(), onClick = { onPick(market.id) }, contentPadding = PaddingValues(14.dp)) {
                        Text("🛒  ${market.name}", style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink)
                    }
                }
                if (markets.isEmpty()) {
                    Text(
                        "Dê um nome. Ele já vem com os corredores padrão, que você ajusta depois.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KekTheme.colors.muted,
                    )
                    KekTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "Ex.: Mercado da esquina",
                        modifier = Modifier.fillMaxWidth(),
                        onDone = { onCreate(newName) },
                    )
                }
            }
        },
        confirmButton = {
            if (markets.isEmpty()) {
                TextButton(onClick = { onCreate(newName) }) { Text("Criar e ir") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
