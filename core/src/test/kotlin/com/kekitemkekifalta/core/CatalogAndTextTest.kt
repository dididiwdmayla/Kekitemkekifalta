package com.kekitemkekifalta.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogAndTextTest {

    @Test
    fun catalogHasAboutOneHundredFiftyUniqueItems() {
        assertTrue(Catalog.entries.size >= 140, "size ${Catalog.entries.size}")
        assertEquals(Catalog.entries.size, Catalog.entries.map { it.key }.toSet().size, "duplicate names")
        Sector.entries.forEach { sector ->
            assertTrue(Catalog.entries.any { it.sector == sector }, "no items for $sector")
        }
        assertTrue(Catalog.entries.all { it.days > 0 })
    }

    @Test
    fun catalogExamplesFromTheSpec() {
        val banana = assertNotNull(Catalog.find("banana"))
        assertEquals(ClockType.SPOILS, banana.clock)
        assertEquals(4.0, banana.days)
        val suco = assertNotNull(Catalog.find("suco aberto"))
        assertEquals(5.0, suco.days)
        assertEquals(ClockType.RUNS_OUT, assertNotNull(Catalog.find("ARROZ")).clock)
        assertEquals("Mandioca", Catalog.find("aipim")?.name)
        assertNull(Catalog.find("disco voador"))
    }

    @Test
    fun normalizerStripsAccentsAndSpaces() {
        assertEquals("pao de queijo", TextNormalizer.normalize("  Pão  de   Queijo "))
        assertEquals("Açaí", TextNormalizer.prettyName("açaí"))
    }

    @Test
    fun autocompleteRanksPrefixFirstAndUsesAliases() {
        val candidates = listOf("Leite condensado", "Leite", "Creme de leite", "Alface").map { Candidate(it, emptyList(), it) } +
            Candidate("Mandioca", listOf("aipim"), "Mandioca")
        assertEquals(listOf("Leite", "Leite condensado", "Creme de leite"), Autocomplete.rank("lei", candidates).map { it.value })
        assertEquals(listOf("Mandioca"), Autocomplete.rank("aip", candidates).map { it.value })
        assertTrue(Autocomplete.rank("   ", candidates).isEmpty())
    }

    @Test
    fun quantityFormatting() {
        assertEquals("2", QuantityFormat.number(2.0))
        assertEquals("1,5 kg", QuantityFormat.format(1.5, "kg"))
        assertEquals("pacote", QuantityFormat.format(null, "pacote"))
        assertNull(QuantityFormat.format(null, " "))
        assertEquals(0.5, QuantityFormat.parse("0,5"))
        assertNull(QuantityFormat.parse("abc"))
        assertNull(QuantityFormat.parse("0"))
    }

    @Test
    fun shoppingListTextLooksRight() {
        val text = ShoppingListText.format(
            "kekifalta", "Mercado X",
            listOf(
                ShoppingListText.Section("Hortifruti", listOf(ShoppingListText.Line("Banana"), ShoppingListText.Line("Tomate", "1 kg", "fundo"))),
                ShoppingListText.Section("Vazio", emptyList()),
            ),
        )
        val expected = "*kekifalta*\nMercado X\n\n*Hortifruti*\n☐ Banana\n☐ Tomate (1 kg) — fundo\n\n2 itens"
        assertEquals(expected, text)
        assertTrue(ShoppingListText.format("kekifalta", null, emptyList()).contains("Nada faltando"))
    }

    @Test
    fun dailySummaryMessage() {
        assertEquals("3 itens acabando, 2 provavelmente acabaram", DailySummary.message(3, 2))
        assertEquals("1 item acabando", DailySummary.message(1, 0))
        assertEquals("1 provavelmente acabou", DailySummary.message(0, 1))
        assertNull(DailySummary.message(0, 0))
    }

    @Test
    fun dailyDelayPointsToNextOccurrence() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val now = java.time.ZonedDateTime.of(2026, 10, 2, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(60L * 60 * 1000, DailySummary.delayUntilNext(now, 9 * 60, zone))
        assertEquals(23L * 60 * 60 * 1000, DailySummary.delayUntilNext(now, 7 * 60, zone))
        assertEquals(24L * 60 * 60 * 1000, DailySummary.delayUntilNext(now, 8 * 60, zone))
    }
}
