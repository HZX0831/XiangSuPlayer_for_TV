package com.cutedino.xiangsuplayer.core.audio

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cutedino.xiangsuplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackSnapshotStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
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

    @Test fun rejectsCorruptAndUnsupportedSnapshots() {
        file.writeText("""{"version":2,"queue":[]}""")
        assertNull(store.read())
        file.writeText("{")
        assertNull(store.read())
    }
}
