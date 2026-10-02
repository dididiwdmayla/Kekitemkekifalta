package com.kekitemkekifalta.core

/** How an item's clock runs out: it spoils (banana) or gets used up (rice). */
enum class ClockType(val key: String, val label: String) {
    SPOILS("spoils", "estraga"),
    RUNS_OUT("runs_out", "acaba");

    companion object {
        fun fromKey(key: String?): ClockType = entries.firstOrNull { it.key == key } ?: RUNS_OUT
    }
}

/**
 * Fixed set of sectors. [key] is what gets stored and synced, never rename it.
 * The declaration order is also the default market route.
 */
enum class Sector(
    val key: String,
    val label: String,
    val emoji: String,
    val defaultDays: Double,
    val defaultClock: ClockType,
) {
    HORTIFRUTI("hortifruti", "Hortifruti", "🥬", 5.0, ClockType.SPOILS),
    PADARIA("padaria", "Padaria", "🥖", 3.0, ClockType.SPOILS),
    ACOUGUE_FRIOS("acougue_frios", "Açougue e frios", "🥩", 4.0, ClockType.SPOILS),
    LATICINIOS("laticinios", "Laticínios", "🧀", 7.0, ClockType.SPOILS),
    MERCEARIA("mercearia", "Mercearia", "🥫", 30.0, ClockType.RUNS_OUT),
    BEBIDAS("bebidas", "Bebidas", "🧃", 7.0, ClockType.RUNS_OUT),
    CONGELADOS("congelados", "Congelados", "🧊", 30.0, ClockType.RUNS_OUT),
    LIMPEZA("limpeza", "Limpeza", "🧽", 30.0, ClockType.RUNS_OUT),
    HIGIENE("higiene", "Higiene", "🧼", 30.0, ClockType.RUNS_OUT),
    PET("pet", "Pet", "🐾", 30.0, ClockType.RUNS_OUT),
    OUTROS("outros", "Outros", "📦", 14.0, ClockType.RUNS_OUT);

    companion object {
        fun fromKey(key: String?): Sector = entries.firstOrNull { it.key == key } ?: OUTROS

        /** Stored as a comma separated list of keys (aisle -> sectors). */
        fun decodeList(csv: String?): List<Sector> =
            csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .mapNotNull { key -> entries.firstOrNull { it.key == key } }
                .distinct()

        fun encodeList(sectors: Collection<Sector>): String =
            sectors.distinct().joinToString(",") { it.key }
    }
}
