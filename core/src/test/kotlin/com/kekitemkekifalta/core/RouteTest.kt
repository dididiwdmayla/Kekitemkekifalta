package com.kekitemkekifalta.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteTest {
    private data class Thing(val name: String, val sector: Sector, val override: String? = null)

    private val aisles = listOf(
        AisleSpec("a1", "Corredor 1", listOf(Sector.HORTIFRUTI)),
        AisleSpec("a2", "Corredor 2", listOf(Sector.LATICINIOS, Sector.ACOUGUE_FRIOS)),
        AisleSpec("a3", "Corredor 3", listOf(Sector.MERCEARIA)),
    )

    @Test
    fun groupsByAisleInRouteOrderWithLooseItemsLast() {
        val items = listOf(
            Thing("Leite", Sector.LATICINIOS),
            Thing("Banana", Sector.HORTIFRUTI),
            Thing("Arroz", Sector.MERCEARIA),
            Thing("Sabão", Sector.LIMPEZA),
            Thing("Abacate", Sector.HORTIFRUTI),
        )
        val groups = RouteGrouping.group(aisles, items, { it.sector }, { it.override }, { it.name })
        assertEquals(listOf("a1", "a2", "a3", null), groups.map { it.aisle?.id })
        assertEquals(listOf("Abacate", "Banana"), groups[0].items.map { it.name })
        assertEquals(listOf("Sabão"), groups[3].items.map { it.name })
    }

    @Test
    fun overrideWinsOverSector() {
        val items = listOf(Thing("Café", Sector.MERCEARIA, override = "a1"), Thing("Pão", Sector.PADARIA, override = "gone"))
        val groups = RouteGrouping.group(aisles, items, { it.sector }, { it.override }, { it.name })
        assertEquals(listOf("Café"), groups[0].items.map { it.name })
        assertEquals(listOf("Pão"), groups.last().items.map { it.name })
    }

    @Test
    fun emptyAislesStillPresent() {
        val groups = RouteGrouping.group(aisles, emptyList<Thing>(), { it.sector }, { null }, { it.name })
        assertEquals(3, groups.size)
    }

    @Test
    fun noProposalWhenOrderMatches() {
        assertNull(RouteLearning.proposeRoute(listOf("a", "b", "c"), listOf("a", "a", "b", "c")))
        assertNull(RouteLearning.proposeRoute(listOf("a", "b", "c"), listOf("a", "c")))
    }

    @Test
    fun noProposalWithSingleAisle() {
        assertNull(RouteLearning.proposeRoute(listOf("a", "b"), listOf("b", "b")))
    }

    @Test
    fun proposesObservedOrderKeepingUnvisitedSlots() {
        val proposal = RouteLearning.proposeRoute(listOf("a", "b", "c", "d"), listOf("c", "a"))
        assertEquals(listOf("c", "b", "a", "d"), proposal)
    }

    @Test
    fun medianIgnoresOneLatePick() {
        // Everything from aisle a was picked first except one banana grabbed at the very end.
        val checks = listOf("a", "a", "a", "b", "b", "c", "a")
        assertNull(RouteLearning.proposeRoute(listOf("a", "b", "c"), checks))
    }

    @Test
    fun unknownAislesAreIgnored() {
        assertEquals(listOf("b", "a"), RouteLearning.proposeRoute(listOf("a", "b"), listOf("x", "b", "a")))
    }
}
