package com.kekitemkekifalta.core

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object QuantityFormat {
    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    /** 2.0 -> "2", 1.5 -> "1,5", 0.25 -> "0,25". */
    fun number(value: Double): String =
        DecimalFormat("#,##0.##", DecimalFormatSymbols(ptBr)).format(value)

    /** "2 kg", "3", "pacote"; null when there is nothing to show. */
    fun format(quantity: Double?, unit: String?): String? {
        val u = unit?.trim().orEmpty()
        return when {
            quantity != null && u.isNotEmpty() -> "${number(quantity)} $u"
            quantity != null -> number(quantity)
            u.isNotEmpty() -> u
            else -> null
        }
    }

    /** Lenient parse of what people type: "1,5", "2", "0.5". */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
}

/** Plain-text shopping list, formatted to read well in WhatsApp. */
object ShoppingListText {
    data class Line(val name: String, val quantity: String? = null, val note: String? = null)
    data class Section(val title: String, val lines: List<Line>)

    fun format(title: String, subtitle: String?, sections: List<Section>): String = buildString {
        append("*").append(title).append("*")
        if (!subtitle.isNullOrBlank()) append("\n").append(subtitle)
        val nonEmpty = sections.filter { it.lines.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            append("\n\nNada faltando. 🎉")
            return@buildString
        }
        nonEmpty.forEach { section ->
            append("\n\n*").append(section.title).append("*")
            section.lines.forEach { line ->
                append("\n☐ ").append(line.name)
                if (!line.quantity.isNullOrBlank()) append(" (").append(line.quantity).append(")")
                if (!line.note.isNullOrBlank()) append(" — ").append(line.note)
            }
        }
        val total = nonEmpty.sumOf { it.lines.size }
        append("\n\n").append(if (total == 1) "1 item" else "$total itens")
    }
}
