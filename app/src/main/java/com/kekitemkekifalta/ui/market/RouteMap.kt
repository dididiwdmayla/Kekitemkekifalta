@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.kekitemkekifalta.ui.market

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kekitemkekifalta.R
import com.kekitemkekifalta.data.db.AisleEntity
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.quantityLabel
import com.kekitemkekifalta.ui.components.ChoiceChip
import com.kekitemkekifalta.ui.components.KekIcon
import com.kekitemkekifalta.ui.components.KekTextField
import com.kekitemkekifalta.ui.components.Pill
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.theme.KekTheme

/**
 * The schematic map: one block per aisle, top to bottom in walking order, linked by a dashed path.
 * Empty aisles are collapsed to a single line; the current aisle is highlighted.
 */
fun LazyListScope.routeMap(
    groups: List<MapGroup>,
    currentKey: String?,
    shopping: Boolean,
    expanded: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onItemClick: (MapItem) -> Unit,
    onItemLongClick: (MapItem) -> Unit,
) {
    groups.forEachIndexed { index, group ->
        if (index > 0) {
            item(key = "link-${group.key}") { RouteLink() }
        }
        item(key = "aisle-${group.key}") {
            val done = shopping && group.items.isNotEmpty() && group.pending == 0
            val collapsed = group.items.isEmpty() || (done && group.key !in expanded)
            AisleBlock(
                group = group,
                current = group.key == currentKey,
                collapsed = collapsed,
                done = done,
                shopping = shopping,
                onHeaderClick = { if (done) onToggleExpanded(group.key) },
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
            )
        }
    }
}

@Composable
private fun RouteLink() {
    val color = KekTheme.colors.outline
    Canvas(
        Modifier
            .padding(start = 38.dp)
            .width(4.dp)
            .height(18.dp),
    ) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}

@Composable
private fun AisleNumber(text: String, highlighted: Boolean) {
    val c = KekTheme.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (highlighted) c.ink else c.card)
            .border(2.dp, c.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (highlighted) c.paper else c.ink)
    }
}

@Composable
private fun AisleBlock(
    group: MapGroup,
    current: Boolean,
    collapsed: Boolean,
    done: Boolean,
    shopping: Boolean,
    onHeaderClick: () -> Unit,
    onItemClick: (MapItem) -> Unit,
    onItemLongClick: (MapItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KekTheme.colors
    val name = group.aisle?.name ?: "Sem corredor"
    val number = group.position?.toString() ?: "?"
    val sectors = group.aisle?.sectors.orEmpty()
    val sectorsLabel = sectors.joinToString(" · ") { it.emoji + " " + it.label }
        .takeIf { sectors.isNotEmpty() && !(sectors.size == 1 && sectors.first().label == name) }

    if (group.items.isEmpty()) {
        // Collapsed, nothing to pick here.
        Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            AisleNumber(number, highlighted = false)
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("nada aqui", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        return
    }

    val background by animateColorAsState(
        when {
            current -> c.mustardSoft
            done -> c.leafSoft
            else -> c.card
        },
        label = "aisle",
    )
    StickerCard(modifier.fillMaxWidth(), color = background, contentPadding = PaddingValues(0.dp), elevation = if (current) 5.dp else 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = done, onClick = onHeaderClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AisleNumber(number, highlighted = current)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sectorsLabel != null) {
                    Text(sectorsLabel, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                done -> Pill("✓ tudo pego", c.card)
                current && shopping -> Pill("você está aqui", c.card)
                else -> Pill("${group.pending}", if (group.pending > 0) c.tomatoSoft else c.leafSoft)
            }
        }
        if (!collapsed) {
            group.items.forEach { mapItem ->
                HorizontalDivider(color = c.outline.copy(alpha = 0.18f))
                MapItemRow(mapItem, shopping, onClick = { onItemClick(mapItem) }, onLongClick = { onItemLongClick(mapItem) })
            }
        }
    }
}

@Composable
private fun MapItemRow(mapItem: MapItem, shopping: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = KekTheme.colors
    val ink = c.ink
    val item = mapItem.item
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (shopping) 64.dp else 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (shopping) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (mapItem.checked) c.leaf else c.card)
                    .border(2.5.dp, c.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (mapItem.checked) KekIcon(R.drawable.ic_check, "Pego", tint = c.onAccent, size = 20.dp)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = if (shopping) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = if (mapItem.checked) ink.copy(alpha = 0.45f) else ink,
                textDecoration = if (mapItem.checked) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(item.quantityLabel, item.note).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (mapItem.shelfNote != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KekIcon(R.drawable.ic_pin, null, tint = ink.copy(alpha = 0.7f), size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(mapItem.shelfNote, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Where an item sits in this market: free text plus an optional fixed aisle. */
@Composable
fun ShelfNoteDialog(
    item: ItemEntity,
    aisles: List<AisleEntity>,
    initialNote: String,
    initialAisleId: String?,
    onDismiss: () -> Unit,
    onSave: (note: String, aisleId: String?) -> Unit,
) {
    var note by remember { mutableStateOf(initialNote) }
    var aisleId by remember { mutableStateOf(initialAisleId?.takeIf { id -> aisles.any { it.id == id } }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KekTheme.colors.paper,
        title = { Text("${item.name} neste mercado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KekTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Ex.: prateleira de baixo, lado direito",
                    label = "Anotação de prateleira",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Corredor", style = MaterialTheme.typography.labelLarge, color = KekTheme.colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Pelo setor", selected = aisleId == null, onClick = { aisleId = null })
                    aisles.forEachIndexed { index, aisle ->
                        ChoiceChip("${index + 1}. ${aisle.name}", selected = aisleId == aisle.id, onClick = { aisleId = aisle.id })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note, aisleId) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
