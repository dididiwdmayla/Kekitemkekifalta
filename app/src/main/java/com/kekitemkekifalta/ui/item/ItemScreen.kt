@file:OptIn(ExperimentalLayoutApi::class)

package com.kekitemkekifalta.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.kekitemkekifalta.core.QuantityFormat
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.StockClock
import com.kekitemkekifalta.core.StockState
import com.kekitemkekifalta.data.ItemEdit
import com.kekitemkekifalta.data.clock
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.EndReason
import com.kekitemkekifalta.data.db.ItemCycleEntity
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.MarketEntity
import com.kekitemkekifalta.data.db.ShelfNoteEntity
import com.kekitemkekifalta.data.expectedDays
import com.kekitemkekifalta.data.isHave
import com.kekitemkekifalta.data.sectorEnum
import com.kekitemkekifalta.data.stockState
import com.kekitemkekifalta.ui.common.formatDate
import com.kekitemkekifalta.ui.common.formatDays
import com.kekitemkekifalta.ui.common.tickerFlow
import com.kekitemkekifalta.ui.components.ChoiceChip
import com.kekitemkekifalta.ui.components.FuelBar
import com.kekitemkekifalta.ui.components.IconCircleButton
import com.kekitemkekifalta.ui.components.KekButton
import com.kekitemkekifalta.ui.components.KekGhostButton
import com.kekitemkekifalta.ui.components.KekTextField
import com.kekitemkekifalta.ui.components.ScreenHeader
import com.kekitemkekifalta.ui.components.SectionTitle
import com.kekitemkekifalta.ui.components.SectorChip
import com.kekitemkekifalta.ui.components.StatusPill
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.market.ShelfNoteDialog
import com.kekitemkekifalta.ui.theme.KekTheme
import com.kekitemkekifalta.ui.theme.inkOnLight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class ItemUi(
    val item: ItemEntity? = null,
    val state: StockState? = null,
    val cycles: List<ItemCycleEntity> = emptyList(),
    val markets: List<MarketEntity> = emptyList(),
    val notes: Map<String, ShelfNoteEntity> = emptyMap(),
    val aislesByMarket: Map<String, List<AisleEntity>> = emptyMap(),
    val loaded: Boolean = false,
)

class ItemViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val base = combine(
        c.items.observeItem(id),
        c.items.observeCycles(id),
        c.markets.observeMarkets(),
        c.markets.observeShelfNotesForItem(id),
        c.markets.observeAllAisles(),
    ) { item, cycles, markets, notes, aisles ->
        ItemUi(
            item = item,
            cycles = cycles,
            markets = markets,
            notes = notes.associateBy { it.marketId },
            aislesByMarket = aisles.groupBy { it.marketId }.mapValues { (_, list) -> list.sortedBy { it.position } },
            loaded = true,
        )
    }

    val ui: StateFlow<ItemUi> = combine(base, tickerFlow()) { ui, now -> ui.copy(state = ui.item?.stockState(now)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemUi())

    /** Saves the form. [days] is only applied when the user changed the duration. */
    fun save(edit: ItemEdit, days: Double?) = c.appScope.launch {
        c.items.edit(id, edit)
        if (days != null) c.items.setDuration(id, days)
    }

    fun finished() = c.appScope.launch { c.items.markFinished(id)?.let { c.messages.emit(UiMessage.Undo(it)) } }
    fun spoiled() = c.appScope.launch { c.items.markSpoiled(id)?.let { c.messages.emit(UiMessage.Undo(it)) } }
    fun bought() = c.appScope.launch { c.items.restock(id)?.let { c.messages.emit(UiMessage.Undo(it)) } }
    fun delete() = c.appScope.launch { c.items.delete(id)?.let { c.messages.emit(UiMessage.Undo(it)) } }
    fun setShelfNote(marketId: String, note: String, aisleId: String?) = c.appScope.launch {
        c.markets.setShelfNote(id, marketId, note, aisleId)
    }
}

@Composable
fun ItemScreen(itemId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ItemViewModel = viewModel { ItemViewModel(context.container, itemId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val item = ui.item

    if (ui.loaded && item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    if (item == null) return

    var initialized by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var sectorKey by rememberSaveable { mutableStateOf(Sector.OUTROS.key) }
    var clockKey by rememberSaveable { mutableStateOf(ClockType.RUNS_OUT.key) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("") }
    var initialDays by rememberSaveable { mutableStateOf("") }
    var noteForMarket by remember { mutableStateOf<MarketEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        if (!initialized) {
            name = item.name
            sectorKey = item.sector
            clockKey = item.clockType
            quantity = item.quantity?.let { QuantityFormat.number(it) }.orEmpty()
            unit = item.unit.orEmpty()
            note = item.note.orEmpty()
            initialDays = QuantityFormat.number(Math.round(item.expectedDays * 10) / 10.0)
            days = initialDays
            initialized = true
        }
    }

    fun save() {
        val parsedDays = QuantityFormat.parse(days)?.takeIf { days.trim() != initialDays && abs(it - item.expectedDays) > 0.05 }
        vm.save(
            ItemEdit(
                name = name.ifBlank { item.name },
                sector = Sector.fromKey(sectorKey),
                clock = ClockType.fromKey(clockKey),
                quantity = QuantityFormat.parse(quantity),
                unit = unit,
                note = note,
            ),
            parsedDays,
        )
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        ScreenHeader(title = item.name, subtitle = "${item.sectorEnum.emoji} ${item.sectorEnum.label}", onBack = onBack) {
            IconCircleButton(R.drawable.ic_check, "Salvar", { save() }, color = KekTheme.colors.leaf, tint = KekTheme.colors.onAccent)
        }

        StatusCard(item, ui.state, onFinished = { vm.finished() }, onSpoiled = { vm.spoiled() }, onBought = { vm.bought() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        FormSection("Nome") {
            KekTextField(name, { name = it }, placeholder = "Nome", modifier = Modifier.fillMaxWidth())
        }
        FormSection("Setor") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Sector.entries.forEach { sector -> SectorChip(sector, selected = sector.key == sectorKey, onClick = { sectorKey = sector.key }) }
            }
        }
        FormSection("Relógio") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("estraga", selected = clockKey == ClockType.SPOILS.key, onClick = { clockKey = ClockType.SPOILS.key }, leading = "🍌")
                ChoiceChip("acaba", selected = clockKey == ClockType.RUNS_OUT.key, onClick = { clockKey = ClockType.RUNS_OUT.key }, leading = "🍚")
            }
            Text(
                if (clockKey == ClockType.SPOILS.key) "Perecível: o tempo é a validade, não muda com a quantidade." else "Consumo: comprar o dobro dura o dobro.",
                style = MaterialTheme.typography.bodySmall,
                color = KekTheme.colors.muted,
            )
        }
        FormSection("Quantidade (opcional)") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KekTextField(
                    quantity,
                    { quantity = it },
                    placeholder = "Ex.: 2",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                KekTextField(unit, { unit = it }, placeholder = "Unidade", modifier = Modifier.weight(1.4f), imeAction = ImeAction.Next)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("un", "kg", "g", "L", "ml", "pacote", "caixa", "dúzia").forEach { u ->
                    ChoiceChip(u, selected = unit == u, onClick = { unit = if (unit == u) "" else u })
                }
            }
        }
        FormSection("Duração estimada (dias)") {
            KekTextField(
                days,
                { days = it },
                placeholder = "dias",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                onDone = { save() },
            )
            val learned = item.learnedCycles
            Text(
                when {
                    learned == 0 -> "Chute inicial. O app ajusta sozinho a cada vez que acaba."
                    learned == 1 -> "Aprendido com 1 ciclo. Mude se quiser: o app continua ajustando."
                    else -> "Aprendido com $learned ciclos. Mude se quiser: o app continua ajustando."
                },
                style = MaterialTheme.typography.bodySmall,
                color = KekTheme.colors.muted,
            )
        }
        FormSection("Observação") {
            KekTextField(note, { note = it }, placeholder = "Ex.: a marca que a gente gosta", modifier = Modifier.fillMaxWidth(), singleLine = false, imeAction = ImeAction.Default)
        }

        if (ui.markets.isNotEmpty()) {
            FormSection("Onde fica em cada mercado") {
                ui.markets.forEach { market ->
                    val shelf = ui.notes[market.id]
                    val aisleName = shelf?.aisleId?.let { aid -> ui.aislesByMarket[market.id]?.firstOrNull { it.id == aid }?.name }
                    StickerCard(Modifier.fillMaxWidth(), onClick = { noteForMarket = market }) {
                        Text("🛒 ${market.name}", style = MaterialTheme.typography.titleSmall, color = KekTheme.colors.ink)
                        Text(
                            listOfNotNull(aisleName?.let { "corredor: $it" }, shelf?.note?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "Toque para anotar a prateleira" },
                            style = MaterialTheme.typography.bodySmall,
                            color = KekTheme.colors.muted,
                        )
                    }
                }
            }
        }

        if (ui.cycles.isNotEmpty()) {
            FormSection("Histórico") {
                ui.cycles.take(12).forEach { cycle -> CycleLine(cycle) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KekButton("Salvar", { save() }, Modifier.weight(1f), icon = R.drawable.ic_check, color = KekTheme.colors.leaf, big = true)
            KekGhostButton("Excluir", { confirmDelete = true }, icon = R.drawable.ic_trash, big = true)
        }
    }

    noteForMarket?.let { market ->
        val shelf = ui.notes[market.id]
        ShelfNoteDialog(
            item = item,
            aisles = ui.aislesByMarket[market.id].orEmpty(),
            initialNote = shelf?.note.orEmpty(),
            initialAisleId = shelf?.aisleId,
            onDismiss = { noteForMarket = null },
            onSave = { text, aisleId ->
                vm.setShelfNote(market.id, text, aisleId)
                noteForMarket = null
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = KekTheme.colors.paper,
            title = { Text("Excluir ${item.name}?") },
            text = { Text("Some do kekitem e do kekifalta. Dá pra desfazer logo depois.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                    onBack()
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun StatusCard(
    item: ItemEntity,
    state: StockState?,
    onFinished: () -> Unit,
    onSpoiled: () -> Unit,
    onBought: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StickerCard(modifier.fillMaxWidth()) {
        if (item.isHave && state != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Em casa desde ${item.stockedAt?.let(::formatDate).orEmpty()}", style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink, modifier = Modifier.weight(1f))
                StatusPill(state.status)
            }
            Spacer(Modifier.height(8.dp))
            FuelBar(state, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                "${StockClock.remainingLabel(state)} · dura ~${formatDays(state.expectedDays)} (${item.clock.label})",
                style = MaterialTheme.typography.bodySmall,
                color = KekTheme.colors.muted,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KekButton("acabou", onFinished, Modifier.weight(1f), icon = R.drawable.ic_check, color = KekTheme.colors.mustard, contentColor = KekTheme.colors.inkOnLight)
                KekButton("estragou", onSpoiled, Modifier.weight(1f), icon = R.drawable.ic_waste)
            }
        } else {
            Text("Está no kekifalta", style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink)
            Text("Dura ~${formatDays(item.expectedDays)} (${item.clock.label})", style = MaterialTheme.typography.bodySmall, color = KekTheme.colors.muted)
            Spacer(Modifier.height(12.dp))
            KekButton("comprei", onBought, Modifier.fillMaxWidth(), icon = R.drawable.ic_check, color = KekTheme.colors.leaf)
        }
    }
}

@Composable
private fun CycleLine(cycle: ItemCycleEntity) {
    val ended = cycle.endedAt
    val text = if (ended == null) {
        "Em casa desde ${formatDate(cycle.startedAt)}"
    } else {
        val length = StockClock.elapsedDays(cycle.startedAt, ended)
        val how = if (cycle.endReason == EndReason.SPOILED) "estragou" else "acabou"
        "${formatDate(cycle.startedAt)} → ${formatDate(ended)} · ${formatDays(length)} · $how"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (cycle.endReason == EndReason.SPOILED) "🥀" else if (ended == null) "🏠" else "✓", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KekTheme.colors.ink)
    }
}
