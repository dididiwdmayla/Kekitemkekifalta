package com.kekitemkekifalta.core

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    /** Lowercase, no accents, single spaces: "  Pão  de Queijo " -> "pao de queijo". */
    fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(spaces, " ")

    /** Capitalizes the first letter only, keeping the rest as typed. */
    fun prettyName(text: String): String {
        val clean = text.trim().replace(spaces, " ")
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("pt-BR")) else it.toString() }
    }
}
