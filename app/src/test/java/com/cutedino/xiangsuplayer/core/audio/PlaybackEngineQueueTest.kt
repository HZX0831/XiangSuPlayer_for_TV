package com.cutedino.xiangsuplayer.core.audio

import android.app.Application
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import com.cutedino.xiangsuplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackEngineQueueTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun song(number: Int) = Song(number.toString(), "Track $number", "", "", source = "local:test")

    private fun withEngine(test: (PlaybackEngine, PlaybackState) -> Unit) {
        val player = ExoPlayer.Builder(context).build()
        val state = PlaybackState()
        val engine = PlaybackEngine(context, player, state)
        try {
            test(engine, state)
        } finally {
            engine.close()
            player.release()
        }
    }

    @Test fun karaokeOpenedBeforeDiskRestorePreservesOrdinaryQueue() {
        val queue = (1..3).map { PlaybackQueueEntry(song(it)) }
        PlaybackSnapshotStore(context).write(PlaybackSnapshot(queue, queue[1].id, 7000L, PlayMode.SEQUENCE))
        withEngine { engine, state ->
            engine.beginKaraoke()
            enterWhenReady(engine)
            engine.endKaraoke()
            assertEquals(queue, state.ui.value.queue)
            assertEquals(queue[1].id, state.ui.value.currentEntryId)
            assertEquals(7000L, state.ui.value.positionMs)
        }
    }

    @Test fun explicitNextSurvivesModeChangeAndLongQueueSurvivesKaraoke() {
        val songs = (1..501).map(::song)
        withEngine { engine, state ->
            engine.playSong(songs[0], songs)
            engine.togglePlayMode()
            engine.insertAsNext(songs[2])
            engine.togglePlayMode()
            val ordinary = state.ui.value.queue
            assertEquals(songs[2].key, ordinary[1].song.key)
            engine.beginKaraoke()
            enterWhenReady(engine)
            engine.endKaraoke()
            assertEquals(501, state.ui.value.queue.size)
            assertEquals(ordinary, state.ui.value.queue)
            engine.playNext()
            assertEquals(songs[2].key, state.ui.value.song?.key)
        }
    }

    private fun enterWhenReady(engine: PlaybackEngine) {
        val deadline = System.nanoTime() + 3_000_000_000L
        var accepted = false
        while (!accepted && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            accepted = engine.enqueueKaraoke(song(900))
            if (!accepted) Thread.sleep(5)
        }
        assertTrue("KTV did not open", accepted)
    }
}
