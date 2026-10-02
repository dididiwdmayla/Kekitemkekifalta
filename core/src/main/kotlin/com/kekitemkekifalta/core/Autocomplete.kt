package com.kekitemkekifalta.core

/** Something the quick-add field can suggest: an item already in the house data or a catalog entry. */
data class Candidate<T>(val name: String, val aliases: List<String>, val value: T)

object Autocomplete {

    /**
     * Ranks [candidates] against [query] (accent and case insensitive).
     * Exact > prefix > word prefix > substring; aliases count slightly less than the name.
     */
    fun <T> rank(query: String, candidates: List<Candidate<T>>, limit: Int = 8): List<Candidate<T>> {
        val q = TextNormalizer.normalize(query)
        if (q.isEmpty()) return emptyList()
        return candidates
            .mapNotNull { c ->
                val nameScore = score(q, TextNormalizer.normalize(c.name))
                val aliasScore = c.aliases.mapNotNull { score(q, TextNormalizer.normalize(it)) }.minOrNull()?.plus(0.5)
                val best = listOfNotNull(nameScore, aliasScore).minOrNull() ?: return@mapNotNull null
                best to c
            }
            .sortedWith(compareBy<Pair<Double, Candidate<T>>> { it.first }.thenBy { it.second.name.length }.thenBy { TextNormalizer.normalize(it.second.name) })
            .map { it.second }
            .distinctBy { TextNormalizer.normalize(it.name) }
            .take(limit)
    }

    private fun score(q: String, text: String): Double? = when {
        text == q -> 0.0
        text.startsWith(q) -> 1.0
        text.split(' ', '-').any { it.startsWith(q) } -> 2.0
        text.contains(q) -> 3.0
        else -> null
    }
}
