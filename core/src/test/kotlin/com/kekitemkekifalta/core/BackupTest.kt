package com.kekitemkekifalta.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupTest {
    private val item = ItemDto(
        id = "i1", householdId = "h", name = "Banana", sector = "hortifruti", clockType = "spoils",
        estimatedDays = 4.0, side = "have", stockedAt = 10, createdAt = 1, updatedAt = 5,
    )

    @Test
    fun roundTrip() {
        val backup = BackupFile(
            exportedAt = 99, householdId = "h",
            items = listOf(item),
            cycles = listOf(CycleDto("c1", "h", "i1", startedAt = 10, expectedDays = 4.0, updatedAt = 10)),
            markets = listOf(MarketDto("m1", "h", "Mercado", 1, 1)),
            aisles = listOf(AisleDto("a1", "h", "m1", "Hortifruti", 0, "hortifruti", 1)),
            shelfNotes = listOf(ShelfNoteDto("s1", "h", "i1", "m1", "embaixo", null, 1)),
        )
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun ignoresUnknownFieldsFromNewerMinorVersions() {
        val text = """{"format":"kekitemkekifalta-backup","version":1,"exportedAt":1,"householdId":"h","surprise":true}"""
        assertEquals("h", BackupCodec.decode(text).householdId)
    }

    @Test
    fun rejectsOtherFiles() {
        assertFailsWith<BackupFormatException> { BackupCodec.decode("""{"hello":1}""") }
        assertFailsWith<BackupFormatException> { BackupCodec.decode("not json") }
        assertFailsWith<BackupFormatException> {
            BackupCodec.decode("""{"format":"kekitemkekifalta-backup","version":99,"exportedAt":1,"householdId":"h"}""")
        }
    }

    @Test
    fun mergeKeepsNewestById() {
        val local = listOf(item, item.copy(id = "i2", updatedAt = 50))
        val incoming = listOf(
            item.copy(updatedAt = 9, name = "Banana prata"), // newer -> apply
            item.copy(id = "i2", updatedAt = 40), // older -> skip
            item.copy(id = "i3", updatedAt = 1), // missing -> apply
        )
        val apply = SyncMerge.rowsToApply(local, incoming, { it.id }, { it.updatedAt })
        assertEquals(listOf("i1", "i3"), apply.map { it.id })
        assertEquals("Banana prata", apply.first().name)
    }
}
