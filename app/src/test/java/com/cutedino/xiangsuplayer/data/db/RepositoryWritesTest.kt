package com.cutedino.xiangsuplayer.data.db

import android.app.Application
import org.robolectric.RuntimeEnvironment
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.data.auth.FavoriteSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import net.moriafly.ncm.NcmApi
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RepositoryWritesTest {
    @Test fun outboxCoalescesUpdatesAndKeepsAccountIsolation() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val db = AppDatabase.build(app, "outbox-test.db")
        try {
            val dao = db.songDao()
            val item = Song("42", "Song", "Artist", "").toFavoriteEntity()
            assertTrue(dao.toggleFavorite(item, 1L))
            val first = checkNotNull(dao.nextFavoriteSync(1L, 0L))
            assertNull(dao.nextFavoriteSync(2L, 0L))
            assertFalse(dao.toggleFavorite(item, 1L))
            val second = checkNotNull(dao.nextFavoriteSync(1L, 0L))
            assertFalse(second.liked)
            dao.finishFavoriteSync(1L, 42L, first.token)
            assertEquals(second, dao.nextFavoriteSync(1L, 0L))
            dao.deferFavoriteSync(1L, 42L, second.token, 1, 500L)
            assertNull(dao.nextFavoriteSync(1L, 499L))
            assertEquals(1, dao.nextFavoriteSync(1L, 500L)?.attempts)
            dao.toggleFavorite(item.copy(sourceId = "kw"), 2L)
            assertNull(dao.nextFavoriteSync(2L, Long.MAX_VALUE))
        } finally {
            db.close()
        }
    }

    @Test fun failedFavoriteSyncRetriesAndRemovesTheDeliveredEntry() = runBlocking {
        val db = AppDatabase.build(RuntimeEnvironment.getApplication(), "outbox-retry-test.db")
        val scope = CoroutineScope(Dispatchers.IO)
        try {
            val dao = db.songDao()
            dao.toggleFavorite(Song("42", "Track", "Artist", "").toFavoriteEntity(), 1L)
            val clock = AtomicLong()
            val requests = AtomicInteger()
            val worker = FavoriteSyncWorker(
                dao, scope, currentAccount = { NcmApi.AccountIdentity(1L, 0L) },
                syncLike = { _, _, _ ->
                    if (requests.incrementAndGet() == 1) Result.failure(IOException("offline"))
                    else Result.success(Unit)
                },
                now = clock::get
            )
            withTimeout(5_000L) {
                while (dao.nextFavoriteSync(1L, Long.MAX_VALUE)?.attempts != 1) delay(10L)
                assertNull(dao.nextFavoriteSync(1L, clock.get()))
                clock.set(2_000L)
                worker.notifyChanged()
                while (dao.nextFavoriteSync(1L, Long.MAX_VALUE) != null) delay(10L)
            }
            assertEquals(2, requests.get())
        } finally {
            scope.cancel()
            db.close()
        }
    }

    @Test fun historyIsTrimmedInTheDatabase() = runBlocking(Dispatchers.IO) {
        val app = RuntimeEnvironment.getApplication()
        val db = AppDatabase.build(app, "history-test.db")
        try {
            val dao = db.songDao()
            repeat(202) { index ->
                dao.insertHistory(Song(index.toString(), "Track", "Artist", "").toHistoryEntity()
                    .copy(playedAtMs = index.toLong()))
            }
            db.openHelper.readableDatabase.query("SELECT count(*), min(playedAtMs) FROM play_history").use {
                assertTrue(it.moveToFirst())
                assertEquals(200, it.getInt(0))
                assertEquals(2L, it.getLong(1))
            }
        } finally {
            db.close()
        }
    }
}
