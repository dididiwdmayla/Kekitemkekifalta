@file:OptIn(ExperimentalLayoutApi::class)

package com.kekitemkekifalta.ui.market

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.kekitemkekifalta.container
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.MarketEntity
import com.kekitemkekifalta.ui.components.IconCircleButton
import com.kekitemkekifalta.ui.components.KekButton
import com.kekitemkekifalta.ui.components.KekGhostButton
import com.kekitemkekifalta.ui.components.KekIcon
import com.kekitemkekifalta.ui.components.KekTextField
import com.kekitemkekifalta.ui.components.ReorderableColumn
import com.kekitemkekifalta.ui.components.ScreenHeader
import com.kekitemkekifalta.ui.components.SectorChip
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.theme.KekTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RouteEditorUi(val market: MarketEntity? = null, val aisles: List<AisleEntity> = emptyList(), val loaded: Boolean = false)

class RouteEditorViewModel(private val c: AppContainer, private val marketId: String) : ViewModel() {
    val ui: StateFlow<RouteEditorUi> = combine(c.markets.observeMarket(marketId), c.markets.observeAisles(marketId)) { market, aisles ->
        RouteEditorUi(market, aisles, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteEditorUi())

    fun rename(name: String) = c.appScope.launch { c.markets.renameMarket(marketId, name) }
    fun reorder(ids: List<String>) = c.appScope.launch { c.markets.reorderAisles(marketId, ids) }
    fun saveAisle(aisleId: String?, name: String, sectors: List<Sector>) = c.appScope.launch {
        val id = aisleId ?: c.markets.addAisle(marketId, name)
        if (aisleId != null) c.markets.renameAisle(id, name)
        c.markets.setAisleSectors(id, sectors)
    }
    fun deleteAisle(aisleId: String) = c.appScope.launch { c.markets.deleteAisle(aisleId) }
    fun deleteMarket() = c.appScope.launch { c.markets.deleteMarket(marketId) }
}

/** Editing state of the aisle dialog: null id means a new aisle. */
private data class AisleDraft(val id: String?, val name: String, val sectors: List<Sector>)

@Composable
fun RouteEditorScreen(marketId: String, onBack: () -> Unit, onMarketDeleted: () -> Unit) {
    val context = LocalContext.current
    val vm: RouteEditorViewModel = viewModel { RouteEditorViewModel(context.container, marketId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var order by remember { mutableStateOf(emptyList<AisleEntity>()) }
    var dragging by remember { mutableStateOf(false) }
    var pendingIds by remember { mutableStateOf<List<String>?>(null) }
    var draft by remember { mutableStateOf<AisleDraft?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ui.aisles, dragging, pendingIds) {
        if (dragging) return@LaunchedEffect
        val ids = ui.aisles.map { it.id }
        val pending = pendingIds
        // Keep the dragged order on screen until the database catches up (no flicker back).
        if (pending != null && ids != pending && ids.toSet() == pending.toSet()) return@LaunchedEffect
        pendingIds = null
        order = ui.aisles
    }

    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = 32.dp),
    ) {
        ScreenHeader(title = "Rota", subtitle = ui.market?.name, onBack = onBack) {
            IconCircleButton(R.drawable.ic_edit, "Renomear mercado", { renaming = true })
        }
        Text(
            "Corredores na ordem em que vocês andam. Arraste pelos pontinhos para reordenar e toque num corredor para escolher os setores que ficam nele.",
            style = MaterialTheme.typography.bodyMedium,
            color = KekTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ReorderableColumn(
            items = order,
            keyOf = { it.id },
            onMove = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
            onDragStart = { dragging = true },
            onDragEnd = {
                val ids = order.map { it.id }
                if (ids != ui.aisles.map { it.id }) {
                    pendingIds = ids
                    vm.reorder(ids)
                }
                dragging = false
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            scrollState = scroll,
        ) { aisle, isDragging, handle ->
            val index = order.indexOfFirst { it.id == aisle.id }
            AisleRow(
                position = index + 1,
                aisle = aisle,
                dragging = isDragging,
                handle = handle,
                onClick = { draft = AisleDraft(aisle.id, aisle.name, Sector.decodeList(aisle.sectors)) },
            )
        }
        KekButton(
            "Adicionar corredor",
            onClick = { draft = AisleDraft(null, "Corredor ${order.size + 1}", emptyList()) },
            icon = R.drawable.ic_add,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        KekGhostButton(
            "Excluir este mercado",
            onClick = { confirmDelete = true },
            icon = R.drawable.ic_trash,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    draft?.let { current ->
        AisleDialog(
            draft = current,
            usedElsewhere = order.filter { it.id != current.id }
                .flatMap { aisle -> Sector.decodeList(aisle.sectors).map { it to aisle.name } }
                .toMap(),
            onDismiss = { draft = null },
            onSave = { name, sectors ->
                vm.saveAisle(current.id, name, sectors)
                draft = null
            },
            onDelete = current.id?.let { id ->
                {
                    vm.deleteAisle(id)
                    draft = null
                }
            },
        )
    }
    if (renaming) {
        NameDialog(
            title = "Nome do mercado",
            placeholder = "Nome",
            confirm = "Salvar",
            initial = ui.market?.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                vm.rename(it)
                renaming = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = KekTheme.colors.paper,
            title = { Text("Excluir ${ui.market?.name.orEmpty()}?") },
            text = { Text("A rota e as anotações de prateleira deste mercado somem. Os itens continuam no app.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteMarket()
                    onMarketDeleted()
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AisleRow(position: Int, aisle: AisleEntity, dragging: Boolean, handle: Modifier, onClick: () -> Unit) {
    val sectors = Sector.decodeList(aisle.sectors)
    StickerCard(
        Modifier.fillMaxWidth(),
        color = if (dragging) KekTheme.colors.mustardSoft else KekTheme.colors.card,
        elevation = if (dragging) 6.dp else 3.dp,
        onClick = onClick,
        contentPadding = PaddingValues(start = 6.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KekIcon(R.drawable.ic_drag, "Arrastar", modifier = handle.padding(8.dp), size = 24.dp)
            Text("$position", style = MaterialTheme.typography.titleLarge, color = KekTheme.colors.muted, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                Text(aisle.name, style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (sectors.isEmpty()) "sem setores" else sectors.joinToString(" ") { it.emoji + " " + it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = KekTheme.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KekIcon(R.drawable.ic_chevron_right, null, size = 18.dp, tint = KekTheme.colors.muted)
        }
    }
}

@Composable
private fun AisleDialog(
    draft: AisleDraft,
    usedElsewhere: Map<Sector, String>,
    onDismiss: () -> Unit,
    onSave: (String, List<Sector>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(draft.name) }
    var sectors by remember { mutableStateOf(draft.sectors) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KekTheme.colors.paper,
        title = { Text(if (draft.id == null) "Novo corredor" else "Corredor") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KekTextField(value = name, onValueChange = { name = it }, placeholder = "Nome ou número", label = "Nome", modifier = Modifier.fillMaxWidth())
                Text("Setores neste corredor", style = MaterialTheme.typography.labelLarge, color = KekTheme.colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sector.entries.forEach { sector ->
                        SectorChip(sector, selected = sector in sectors, onClick = {
                            sectors = if (sector in sectors) sectors - sector else sectors + sector
                        })
                    }
                }
                val moving = sectors.filter { it in usedElsewhere && it !in draft.sectors }
                if (moving.isNotEmpty()) {
                    Text(
                        moving.joinToString("\n") { "${it.label} sai de \"${usedElsewhere.getValue(it)}\"." },
                        style = MaterialTheme.typography.bodySmall,
                        color = KekTheme.colors.muted,
                    )
                }
                if (onDelete != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(0.dp))
                        TextButton(onClick = onDelete) { Text("Excluir corredor", color = KekTheme.colors.tomato) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, sectors) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
