package com.kekitemkekifalta.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import com.kekitemkekifalta.AppContainer
import com.kekitemkekifalta.UiMessage
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.data.AddResult
import com.kekitemkekifalta.data.NewItem
import com.kekitemkekifalta.ui.components.AddOption
import com.kekitemkekifalta.ui.components.sideName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Emits the current time now and then every [periodMs], so time-derived states stay fresh. */
fun tickerFlow(periodMs: Long = 60_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMs)
    }
}

/** Applies a quick-add pick on [side] and posts the right snackbar. */
suspend fun AppContainer.applyAddOption(option: AddOption, sector: Sector?, side: String) {
    val newItem = when (option) {
        is AddOption.Existing -> NewItem(option.item.name)
        is AddOption.FromCatalog -> NewItem(option.entry.name)
        is AddOption.New -> NewItem(option.name, sector ?: Sector.OUTROS)
    }
    when (val result = items.add(newItem, side)) {
        is AddResult.Moved -> messages.emit(UiMessage.Undo(result.undo))
        is AddResult.AlreadyThere -> messages.emit(UiMessage.Text("${result.item.name} já está no ${sideName(side)}"))
        is AddResult.Created -> Unit
    }
}

val ptBr: Locale = Locale.forLanguageTag("pt-BR")

fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM", ptBr).format(Date(millis))
fun formatDateFull(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", ptBr).format(Date(millis))
fun formatDateTime(millis: Long): String = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBr).format(Date(millis))

fun formatDays(days: Double): String {
    val rounded = Math.round(days)
    return when {
        days < 1.0 -> "menos de 1 dia"
        rounded == 1L -> "1 dia"
        else -> "$rounded dias"
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
