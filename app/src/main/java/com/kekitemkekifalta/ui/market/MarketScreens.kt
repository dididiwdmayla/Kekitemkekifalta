package com.kekitemkekifalta.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.MarketEntity
import com.kekitemkekifalta.data.isNeed
import com.kekitemkekifalta.ui.components.EmptyState
import com.kekitemkekifalta.ui.components.IconCircleButton
import com.kekitemkekifalta.ui.components.KekButton
import com.kekitemkekifalta.ui.components.KekGhostButton
import com.kekitemkekifalta.ui.components.KekTextField
import com.kekitemkekifalta.ui.components.Pill
import com.kekitemkekifalta.ui.components.ScreenHeader
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.theme.KekTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- Market list (tab) ----------

data class MarketRow(val market: MarketEntity, val aisles: Int)

data class MarketsUi(val markets: List<MarketRow> = emptyList(), val pending: Int = 0, val loaded: Boolean = false)

class MarketsViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<MarketsUi> = combine(c.markets.observeMarkets(), c.markets.observeAllAisles(), c.items.observeItems()) { markets, aisles, items ->
        val counts = aisles.groupingBy { it.marketId }.eachCount()
        MarketsUi(markets.map { MarketRow(it, counts[it.id] ?: 0) }, items.count { it.isNeed }, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketsUi())

    suspend fun create(name: String): String = c.markets.createMarket(name)
}

@Composable
fun MarketsScreen(onOpenMarket: (String) -> Unit) {
    val context = LocalContext.current
    val vm: MarketsViewModel = viewModel { MarketsViewModel(context.container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            ScreenHeader(title = "mercado", subtitle = "seus mercados e a rota de cada um") {
                IconCircleButton(R.drawable.ic_add, "Novo mercado", { creating = true })
            }
        }
        if (ui.loaded && ui.markets.isEmpty()) {
            item(key = "empty") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyState("🗺️", "Nenhum mercado ainda", "Cadastre o mercado onde vocês compram. Ele já vem com os corredores mais comuns, na ordem do trajeto.")
                    KekButton("Cadastrar mercado", { creating = true }, icon = R.drawable.ic_add, big = true)
                }
            }
        }
        items(ui.markets, key = { it.market.id }) { row ->
            StickerCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateItem(),
                onClick = { onOpenMarket(row.market.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(CircleShape).background(KekTheme.colors.mustardSoft).border(2.dp, KekTheme.colors.outline, CircleShape).padding(10.dp),
                    ) {
                        Text("🛒", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.market.name, style = MaterialTheme.typography.titleLarge, color = KekTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${row.aisles} corredores", style = MaterialTheme.typography.bodySmall, color = KekTheme.colors.muted)
                    }
                    if (ui.pending > 0) Pill("${ui.pending} na lista", KekTheme.colors.tomatoSoft)
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Novo mercado",
            placeholder = "Ex.: Atacadão da avenida",
            confirm = "Criar",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                scope.launch { onOpenMarket(vm.create(name)) }
            },
        )
    }
}

@Composable
fun NameDialog(title: String, placeholder: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, initial: String = "") {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KekTheme.colors.paper,
        title = { Text(title) },
        text = {
            KekTextField(value = name, onValueChange = { name = it }, placeholder = placeholder, modifier = Modifier.fillMaxWidth(), onDone = { onConfirm(name) })
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------- Market map ----------

@Composable
fun MarketMapScreen(marketId: String, onBack: () -> Unit, onEditRoute: () -> Unit, onStartShopping: () -> Unit) {
    val context = LocalContext.current
    val vm: MarketMapViewModel = viewModel { MarketMapViewModel(context.container, marketId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var noteFor by remember { mutableStateOf<MapItem?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    if (ui.loaded && ui.market == null) {
        // Deleted meanwhile.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            ScreenHeader(title = ui.market?.name ?: "", subtitle = "mapa do mercado", onBack = onBack) {
                IconCircleButton(R.drawable.ic_edit, "Editar rota", onEditRoute)
            }
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    if (ui.total == 0) "Nada no kekifalta. Rota livre!" else "${ui.total} ${if (ui.total == 1) "item" else "itens"} no kekifalta, na ordem do trajeto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KekTheme.colors.muted,
                )
                Spacer(Modifier.height(12.dp))
                KekButton(
                    if (ui.checked > 0) "Continuar compra" else "Começar compra",
                    onClick = {
                        vm.rememberMarket()
                        onStartShopping()
                    },
                    icon = R.drawable.ic_cart,
                    big = true,
                    enabled = ui.total > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        routeMap(
            groups = ui.groups,
            currentKey = ui.currentKey,
            shopping = false,
            expanded = expanded,
            onToggleExpanded = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
            onItemClick = { noteFor = it },
            onItemLongClick = { noteFor = it },
        )
        item(key = "hint") {
            Text(
                "Toque num item para anotar onde ele fica neste mercado.",
                style = MaterialTheme.typography.bodySmall,
                color = KekTheme.colors.muted,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    noteFor?.let { mapItem ->
        val note = ui.notes[mapItem.item.id]
        ShelfNoteDialog(
            item = mapItem.item,
            aisles = ui.aisles,
            initialNote = note?.note.orEmpty(),
            initialAisleId = note?.aisleId,
            onDismiss = { noteFor = null },
            onSave = { text, aisleId ->
                vm.setShelfNote(mapItem.item.id, text, aisleId)
                noteFor = null
            },
        )
    }
}

// ---------- Shopping mode ----------

@Composable
fun ShoppingScreen(marketId: String, onBack: () -> Unit, onSwitchMarket: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val vm: MarketMapViewModel = viewModel { MarketMapViewModel(c, marketId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var noteFor by remember { mutableStateOf<MapItem?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var proposal by remember { mutableStateOf<List<AisleEntity>?>(null) }
    var switching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // The screen stays on while shopping.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = ui.market?.name ?: "", subtitle = "modo mercado", onBack = onBack) {
            if (ui.markets.size > 1) IconCircleButton(R.drawable.ic_store, "Trocar mercado", { switching = true })
        }
        ShoppingProgress(checked = ui.checked, total = ui.total, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
            if (ui.loaded && ui.total == 0) {
                item(key = "empty") {
                    EmptyState("🧺", "Nada no kekifalta", "Pode voltar pra casa. Ou dar uma volta só pra ver as ofertas.")
                }
            }
            routeMap(
                groups = ui.groups.filter { it.items.isNotEmpty() || ui.total > 0 },
                currentKey = ui.currentKey,
                shopping = true,
                expanded = expanded,
                onToggleExpanded = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
                onItemClick = { vm.toggle(it.item.id, !it.checked) },
                onItemLongClick = { noteFor = it },
            )
            item(key = "hint") {
                Text(
                    "Segure um item para anotar a prateleira.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KekTheme.colors.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(KekTheme.colors.paper)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KekGhostButton("Sair", onBack, big = true)
            KekButton(
                "Concluir compra",
                onClick = { confirmFinish = true },
                icon = R.drawable.ic_check,
                color = KekTheme.colors.leaf,
                big = true,
                enabled = ui.checked > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmFinish) {
        val missing = ui.total - ui.checked
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = KekTheme.colors.paper,
            title = { Text("Concluir compra?") },
            text = {
                Text(
                    "${ui.checked} ${if (ui.checked == 1) "item vai" else "itens vão"} pro kekitem." +
                        if (missing > 0) " $missing ${if (missing == 1) "continua" else "continuam"} no kekifalta." else " Não ficou nada pra trás!",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    scope.launch {
                        val result = withContext(NonCancellable) { vm.finish() }
                        c.messages.emit(UiMessage.Text("${result.bought} ${if (result.bought == 1) "item foi" else "itens foram"} pro kekitem"))
                        if (result.proposedRoute != null) proposal = result.proposedRoute else onBack()
                    }
                }) { Text("Concluir") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Ainda não") } },
        )
    }

    proposal?.let { order ->
        AlertDialog(
            onDismissRequest = {
                proposal = null
                onBack()
            },
            containerColor = KekTheme.colors.paper,
            title = { Text("Atualizar a rota deste mercado?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Você pegou as coisas numa ordem diferente da cadastrada. A nova ordem seria:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    order.forEachIndexed { index, aisle ->
                        Text("${index + 1}. ${aisle.name}", style = MaterialTheme.typography.titleSmall, color = KekTheme.colors.ink)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyRoute(order)
                    proposal = null
                    onBack()
                }) { Text("Atualizar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    proposal = null
                    onBack()
                }) { Text("Manter como está") }
            },
        )
    }

    noteFor?.let { mapItem ->
        val note = ui.notes[mapItem.item.id]
        ShelfNoteDialog(
            item = mapItem.item,
            aisles = ui.aisles,
            initialNote = note?.note.orEmpty(),
            initialAisleId = note?.aisleId,
            onDismiss = { noteFor = null },
            onSave = { text, aisleId ->
                vm.setShelfNote(mapItem.item.id, text, aisleId)
                noteFor = null
            },
        )
    }

    if (switching) {
        AlertDialog(
            onDismissRequest = { switching = false },
            containerColor = KekTheme.colors.paper,
            title = { Text("Trocar de mercado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.markets.forEach { market ->
                        StickerCard(Modifier.fillMaxWidth(), onClick = {
                            switching = false
                            if (market.id != marketId) onSwitchMarket(market.id)
                        }) {
                            Text(
                                (if (market.id == marketId) "✓ " else "🛒 ") + market.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = KekTheme.colors.ink,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { switching = false }) { Text("Fechar") } },
        )
    }
}

@Composable
private fun ShoppingProgress(checked: Int, total: Int, modifier: Modifier = Modifier) {
    val c = KekTheme.colors
    val fraction = if (total == 0) 0f else checked.toFloat() / total
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$checked de $total no carrinho", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = c.ink)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(CircleShape)
                .background(c.card)
                .border(2.dp, c.outline, CircleShape),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(c.leaf),
                )
            }
        }
    }
}
