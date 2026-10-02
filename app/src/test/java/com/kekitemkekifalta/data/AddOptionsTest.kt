package com.kekitemkekifalta.data

import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.db.Side
import com.kekitemkekifalta.ui.components.AddOption
import com.kekitemkekifalta.ui.components.AddOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddOptionsTest {
    private fun item(name: String, side: String) = ItemEntity(
        id = name, householdId = "h", name = name, normalizedName = name.lowercase(), sector = "mercearia",
        quantity = null, unit = null, note = null, clockType = "runs_out", estimatedDays = 30.0, estimateQuantity = null,
        learnedCycles = 0, side = side, stockedAt = null, snoozedUntil = null, inCartAt = null, catalogKey = null,
        createdAt = 0, updatedAt = 0, deletedAt = null,
    )

    @Test
    fun existingItemOnTheOtherSideIsOfferedAndNotDuplicatedFromCatalog() {
        val options = AddOptions.compute("arroz", listOf(item("Arroz", Side.HAVE)), Side.NEED)
        assertEquals(AddOption.Existing::class, options.first()::class)
        assertTrue(options.none { it is AddOption.FromCatalog && it.entry.name == "Arroz" })
        assertTrue(options.none { it is AddOption.New })
    }

    @Test
    fun unknownNameOffersCreation() {
        val options = AddOptions.compute("pão de mel da vó", emptyList(), Side.HAVE)
        assertEquals("Pão de mel da vó", (options.last() as AddOption.New).name)
    }

    @Test
    fun itemAlreadyOnThisSideIsNotOffered() {
        val options = AddOptions.compute("arroz", listOf(item("Arroz", Side.NEED)), Side.NEED)
        assertTrue(options.none { it is AddOption.Existing })
        assertTrue(options.none { it is AddOption.New })
    }
}
