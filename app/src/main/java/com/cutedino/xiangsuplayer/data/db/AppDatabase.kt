package com.cutedino.xiangsuplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, PlayHistoryEntity::class, FavoriteSyncEntry::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun init(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, "tv_player_database").also { INSTANCE = it }
            }
        }

        fun getInstanceOrNull(): AppDatabase? = INSTANCE

        fun getInstance(): AppDatabase = INSTANCE
            ?: error("AppDatabase not initialized!")

        internal fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(migrateToThree(1), migrateToThree(2), migrateToFour)
                .build()

        private val migrateToFour = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE favorite_sync (
                        accountId INTEGER NOT NULL, trackId INTEGER NOT NULL, liked INTEGER NOT NULL,
                        token TEXT NOT NULL, attempts INTEGER NOT NULL, retryAtMs INTEGER NOT NULL,
                        PRIMARY KEY(accountId, trackId)
                    )""".trimIndent()
                )
                db.execSQL("DELETE FROM play_history WHERE rowid NOT IN (SELECT rowid FROM play_history ORDER BY playedAtMs DESC, rowid DESC LIMIT 200)")
                for (table in listOf("favorite_songs", "play_history")) {
                    db.execSQL("UPDATE $table SET songmid = '' WHERE instr(songmid, '://') > 0 OR substr(songmid, 1, 2) = '//'")
                }
            }
        }

        private fun migrateToThree(from: Int) = object : Migration(from, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSongs(db, "favorite_songs", "addedAtMs", hasSourceMetadata = from == 2)
                if (from == 2) {
                    migrateSongs(db, "play_history", "playedAtMs", hasSourceMetadata = true)
                } else {
                    createSongTable(db, "play_history", "playedAtMs")
                }
            }
        }

        private fun migrateSongs(db: SupportSQLiteDatabase, table: String, time: String, hasSourceMetadata: Boolean) {
            val replacement = "${table}_v3"
            createSongTable(db, replacement, time)
            val metadata = if (hasSourceMetadata) "songmid, hash" else "'', ''"
            db.execSQL(
                """
                INSERT INTO $replacement (id, title, artist, album, coverUrl, durationMs, sourceId, songmid, hash, $time)
                SELECT id, title, artist, album, coverUrl, durationMs,
                    CASE LOWER(TRIM(sourceId))
                        WHEN 'netease' THEN 'wy' WHEN 'netease_native' THEN 'wy'
                        WHEN 'qq' THEN 'tx' WHEN 'kugou' THEN 'kg'
                        WHEN 'kuwo' THEN 'kw' WHEN 'migu' THEN 'mg'
                        ELSE LOWER(TRIM(sourceId))
                    END,
                    $metadata, $time FROM $table
                """.trimIndent()
            )
            db.execSQL("DROP TABLE $table")
            db.execSQL("ALTER TABLE $replacement RENAME TO $table")
        }

        private fun createSongTable(db: SupportSQLiteDatabase, table: String, time: String) {
            db.execSQL(
                """
                CREATE TABLE $table (
                    id TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL,
                    album TEXT NOT NULL, coverUrl TEXT NOT NULL, durationMs INTEGER NOT NULL,
                    sourceId TEXT NOT NULL, songmid TEXT NOT NULL, hash TEXT NOT NULL,
                    $time INTEGER NOT NULL, PRIMARY KEY(sourceId, id)
                )
                """.trimIndent()
            )
        }
    }
}
