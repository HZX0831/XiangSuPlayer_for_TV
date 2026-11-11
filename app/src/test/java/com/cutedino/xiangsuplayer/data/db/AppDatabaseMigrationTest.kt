package com.cutedino.xiangsuplayer.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.cutedino.xiangsuplayer.core.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun migrateVersionOne() = verifyMigration(1)

    @Test fun migrateVersionTwo() = verifyMigration(2)

    private fun verifyMigration(version: Int) = runBlocking {
        val name = "upgrade-$version.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            createLegacyTable(db, "favorite_songs", "addedAtMs", version)
            if (version == 2) createLegacyTable(db, "play_history", "playedAtMs", version)
            db.version = version
        }
        val database = AppDatabase.build(context, name)
        try {
            database.let { db ->
                val dao = db.songDao()
                val favorite = dao.getAllSongs().single()
                assertEquals("wy", favorite.sourceId)
                assertEquals(123L, favorite.addedAtMs)
                assertEquals(if (version == 2) "old-mid" else "", favorite.songmid)
                assertEquals(if (version == 2) 1 else 0, dao.getPlayHistoryList().size)
                assertTrue(dao.toggleFavorite(Song("42", "Another recording", "Artist", "Album", source = "kuwo").toFavoriteEntity()))
                assertTrue(dao.isFavorite("wy", "42"))
                assertTrue(dao.isFavorite("kw", "42"))
                dao.deleteFavoriteById("kw", "42")
                assertFalse(dao.isFavorite("kw", "42"))
                assertTrue(dao.isFavorite("wy", "42"))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun createLegacyTable(db: SQLiteDatabase, table: String, time: String, version: Int) {
        val extraColumns = if (version == 2) "songmid TEXT NOT NULL, hash TEXT NOT NULL," else ""
        val extraValues = if (version == 2) "'old-mid', 'old-hash'," else ""
        db.execSQL(
            "CREATE TABLE $table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                "artist TEXT NOT NULL, album TEXT NOT NULL, coverUrl TEXT NOT NULL, " +
                "durationMs INTEGER NOT NULL, sourceId TEXT NOT NULL, $extraColumns $time INTEGER NOT NULL)"
        )
        db.execSQL("INSERT INTO $table VALUES ('42', 'Song', 'Artist', 'Album', '', 456, 'netease', $extraValues 123)")
    }
}
