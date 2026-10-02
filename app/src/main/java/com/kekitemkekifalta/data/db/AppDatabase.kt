package com.kekitemkekifalta.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/** Bump together with a new Migration in [Migrations.ALL] and the exported schema. */
const val DB_VERSION = 1

@Database(
    entities = [
        ItemEntity::class,
        ItemCycleEntity::class,
        MarketEntity::class,
        AisleEntity::class,
        ShelfNoteEntity::class,
    ],
    version = DB_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun cycleDao(): CycleDao
    abstract fun marketDao(): MarketDao
    abstract fun aisleDao(): AisleDao
    abstract fun shelfNoteDao(): ShelfNoteDao

    companion object {
        const val NAME = "kekitem.db"

        /**
         * No destructive fallback, ever: the app is updated in place and data must survive.
         * A missing migration makes Room throw instead of wiping the database.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/**
 * Hand-written migrations, one per version step (N -> N+1). Never edit a released one.
 * Example for the next change:
 *
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE items ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
 *     }
 * }
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
