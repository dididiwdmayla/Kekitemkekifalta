package com.kekitemkekifalta.data

import com.kekitemkekifalta.data.db.DB_VERSION
import com.kekitemkekifalta.data.db.Migrations
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MigrationsTest {

    @Test
    fun everyVersionStepHasAMigration() {
        for (from in 1 until DB_VERSION) {
            assertTrue(
                Migrations.ALL.any { it.startVersion == from && it.endVersion == from + 1 },
                "Falta a migração $from -> ${from + 1} em Migrations.ALL",
            )
        }
    }

    @Test
    fun everyVersionHasAnExportedSchema() {
        val dir = File("schemas/com.kekitemkekifalta.data.db.AppDatabase")
        for (version in 1..DB_VERSION) {
            assertTrue(File(dir, "$version.json").exists(), "Schema $version.json não encontrado em ${dir.absolutePath}")
        }
    }
}
