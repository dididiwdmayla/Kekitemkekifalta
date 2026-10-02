package com.kekitemkekifalta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kekitemkekifalta.R
import com.kekitemkekifalta.core.Autocomplete
import com.kekitemkekifalta.core.Candidate
import com.kekitemkekifalta.core.Catalog
import com.kekitemkekifalta.core.CatalogEntry
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.TextNormalizer
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.Side
import com.kekitemkekifalta.data.sectorEnum
import com.kekitemkekifalta.ui.theme.KekTheme

/** One line of the quick-add dropdown. */
sealed interface AddOption {
    /** An item that exists on the other side: picking it moves it here. */
    data class Existing(val item: ItemEntity) : AddOption

    data class FromCatalog(val entry: CatalogEntry) : AddOption

    /** Not in the catalog: created on the spot, in the sector the user taps. */
    data class New(val name: String) : AddOption
}

object AddOptions {
    fun compute(query: String, items: List<ItemEntity>, targetSide: String): List<AddOption> {
        val q = TextNormalizer.normalize(query)
        if (q.isEmpty()) return emptyList()
        val known = items.map { it.normalizedName }.toSet()
        val candidates = items.filter { it.side != targetSide }.map { Candidate(it.name, emptyList(), it as Any) } +
            Catalog.entries.filter { it.key !in known }.map { Candidate(it.name, it.aliases, it as Any) }
        val ranked = Autocomplete.rank(query, candidates, limit = 6).map { c ->
            when (val v = c.value) {
                is ItemEntity -> AddOption.Existing(v)
                else -> AddOption.FromCatalog(v as CatalogEntry)
            }
        }
        val exact = q in known || Catalog.entries.any { it.key == q }
        return if (exact) ranked else ranked + AddOption.New(TextNormalizer.prettyName(query))
    }
}

@Composable
fun KekTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
    trailing: @Composable (() -> Unit)? = null,
    label: String? = null,
) {
    val c = KekTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = c.muted) },
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        leadingIcon = if (leadingIcon != null) {
            { KekIcon(leadingIcon, null, tint = c.muted) }
        } else {
            null
        },
        trailingIcon = trailing,
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onSearch = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.ink,
            unfocusedBorderColor = c.outline,
            focusedContainerColor = c.card,
            unfocusedContainerColor = c.card,
            cursorColor = c.tomato,
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
            focusedLabelColor = c.ink,
            unfocusedLabelColor = c.muted,
        ),
    )
}

/**
 * Search-or-add field with catalog autocomplete.
 * [onPick] receives the option and, for a new item, the sector tapped.
 */
@Composable
fun QuickAddBar(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<AddOption>,
    placeholder: String,
    existingVerb: String,
    onPick: (AddOption, Sector?) -> Unit,
    modifier: Modifier = Modifier,
    defaultSector: Sector = Sector.OUTROS,
) {
    val c = KekTheme.colors
    Column(modifier) {
        KekTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            leadingIcon = R.drawable.ic_search,
            modifier = Modifier.fillMaxWidth(),
            onDone = {
                options.firstOrNull()?.let { first -> onPick(first, if (first is AddOption.New) defaultSector else null) }
            },
            trailing = if (query.isNotEmpty()) {
                { IconCircleButton(R.drawable.ic_close, "Limpar", { onQueryChange("") }, Modifier.padding(end = 6.dp)) }
            } else {
                null
            },
        )
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            StickerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                options.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.15f))
                    when (option) {
                        is AddOption.Existing -> OptionRow(
                            emoji = option.item.sectorEnum.emoji,
                            title = option.item.name,
                            subtitle = existingVerb,
                            onClick = { onPick(option, null) },
                        )
                        is AddOption.FromCatalog -> OptionRow(
                            emoji = option.entry.sector.emoji,
                            title = option.entry.name,
                            subtitle = "${option.entry.sector.label} · ${option.entry.clock.label}",
                            onClick = { onPick(option, null) },
                        )
                        is AddOption.New -> NewItemRow(option.name, defaultSector) { sector -> onPick(option, sector) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KekTheme.colors.muted, maxLines = 1)
        }
        KekIcon(R.drawable.ic_add, "Adicionar", size = 20.dp)
    }
}

@Composable
private fun NewItemRow(name: String, defaultSector: Sector, onSector: (Sector) -> Unit) {
    val sectors = listOf(defaultSector) + Sector.entries.filter { it != defaultSector }
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            "Criar “$name” em:",
            style = MaterialTheme.typography.titleMedium,
            color = KekTheme.colors.ink,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sectors, key = { it.key }) { sector ->
                SectorChip(sector, selected = sector == defaultSector, onClick = { onSector(sector) })
            }
        }
    }
}

/** Sides, as shown to the user. */
fun sideName(side: String) = if (side == Side.HAVE) "kekitem" else "kekifalta"
