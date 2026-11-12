package com.cutedino.xiangsuplayer.core.audio

import android.app.Application
import org.robolectric.RuntimeEnvironment
import com.cutedino.xiangsuplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackSnapshotStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val store = PlaybackSnapshotStore(context)
    private val file = File(context.noBackupFilesDir, "playback-resume.json")

    @Test fun preservesRepeatedTracksAndStripsTransientUrls() {
        val song = Song("42", "Track", "Artist", "Album",
            coverUrl = "https://example.test/art?credential", songmid = "https://example.test/audio?temporary")
        val first = PlaybackQueueEntry("first", song)
        val second = PlaybackQueueEntry("second", song)
        store.write(PlaybackSnapshot(listOf(first, second), second.id, 5100L, PlayMode.SHUFFLE))
        val restored = checkNotNull(store.read())
        assertEquals(listOf("first", "second"), restored.queue.map { it.id })
        assertEquals(second.id, restored.currentId)
        assertEquals(5100L, restored.positionMs)
        assertEquals(PlayMode.SHUFFLE, restored.mode)
        assertFalse(file.readText().contains("example.test"))
        store.write(null)
        assertNull(store.read())
    }

    @Test fun largeUtf8SnapshotPreservesCurrentTrackAndPosition() {
        val queue = (1..510).map {
            PlaybackQueueEntry("entry-$it", Song(it.toString(), "曲".repeat(1800), "", ""))
        }
        val current = queue[240]
        store.write(PlaybackSnapshot(queue, current.id, 17000L, PlayMode.SEQUENCE))
        val restored = checkNotNull(store.read())
        assertEquals(current.id, restored.currentId)
        assertEquals(current.song.id, restored.queue.first { it.id == current.id }.song.id)
        assertEquals(17000L, restored.positionMs)
        assertTrue(file.length() <= 2_097_152L)
        assertTrue(restored.queue.size in 2..499)
    }

    @Test fun rejectsCorruptAndUnsupportedSnapshots() {
        file.writeText("""{"version":2,"queue":[]}""")
        assertNull(store.read())
        file.writeText("{")
        assertNull(store.read())
    }
}
