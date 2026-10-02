@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kekitemkekifalta.ui.waste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kekitemkekifalta.AppContainer
import com.kekitemkekifalta.R
import com.kekitemkekifalta.container
import com.kekitemkekifalta.core.QuantityFormat
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.data.db.SpoiledRow
import com.kekitemkekifalta.ui.common.formatDate
import com.kekitemkekifalta.ui.common.ptBr
import com.kekitemkekifalta.ui.components.EmptyState
import com.kekitemkekifalta.ui.components.IconCircleButton
import com.kekitemkekifalta.ui.components.Pill
import com.kekitemkekifalta.ui.components.ScreenHeader
import com.kekitemkekifalta.ui.components.StickerCard
import com.kekitemkekifalta.ui.theme.KekTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

data class WasteUi(
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val rows: List<SpoiledRow> = emptyList(),
    val topItem: Pair<String, Int>? = null,
    val loaded: Boolean = false,
)

class WasteViewModel(private val c: AppContainer) : ViewModel() {
    private val offset = MutableStateFlow(0L)

    val ui: StateFlow<WasteUi> = offset.flatMapLatest { months ->
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now(zone).minusMonths(months)
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val label = month.month.getDisplayName(TextStyle.FULL, ptBr).replaceFirstChar { it.titlecase(ptBr) } + " de " + month.year
        c.items.observeSpoiled(from, to).map { rows ->
            val top = rows.groupingBy { it.itemName }.eachCount().maxByOrNull { it.value }?.takeIf { it.value > 1 }?.toPair()
            WasteUi(label, months == 0L, rows, top, loaded = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WasteUi())

    fun previous() {
        offset.value += 1
    }

    fun next() {
        if (offset.value > 0) offset.value -= 1
    }
}

@Composable
fun WasteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: WasteViewModel = viewModel { WasteViewModel(context.container) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") {
            ScreenHeader(title = "Desperdício", subtitle = "o que estragou", onBack = onBack)
        }
        item(key = "month") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircleButton(R.drawable.ic_chevron_left, "Mês anterior", vm::previous)
                Text(ui.monthLabel, style = MaterialTheme.typography.titleLarge, color = KekTheme.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                if (!ui.isCurrentMonth) {
                    IconCircleButton(R.drawable.ic_chevron_right, "Próximo mês", vm::next)
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
        if (ui.loaded && ui.rows.isEmpty()) {
            item(key = "empty") {
                EmptyState("🏆", "Nada estragou", "Nenhum desperdício registrado neste mês. A casa agradece.")
            }
        }
        if (ui.rows.isNotEmpty()) {
            item(key = "summary") {
                StickerCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = KekTheme.colors.tomatoSoft) {
                    Text(
                        "${ui.rows.size} ${if (ui.rows.size == 1) "coisa estragou" else "coisas estragaram"}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = KekTheme.colors.ink,
                    )
                    ui.topItem?.let { (name, count) ->
                        Text(
                            "Campeão do mês: $name ($count vezes). Talvez comprar menos?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KekTheme.colors.ink,
                        )
                    }
                }
            }
        }
        items(ui.rows, key = { it.cycleId }) { row ->
            val sector = Sector.fromKey(row.sector)
            StickerCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sector.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.itemName, style = MaterialTheme.typography.titleMedium, color = KekTheme.colors.ink)
                        Text(
                            "comprado ${formatDate(row.startedAt)} · estragou ${formatDate(row.endedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KekTheme.colors.muted,
                        )
                    }
                    QuantityFormat.format(row.quantity, row.unit)?.let { Pill(it, KekTheme.colors.card) }
                }
            }
        }
    }
}
