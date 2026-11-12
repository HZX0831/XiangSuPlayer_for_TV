package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.audio.PlaybackSourceAttempts
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.builtin.refreshMiguUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SourceBackendRegressionTest {
    @Test fun mediaPreparationDoesNotConsumeTheFirstRecoveryWindow() {
        var now = 0L
        val attempts = PlaybackSourceAttempts { now }
        now = 5_000_000_000L
        assertEquals(0L, attempts.remaining())
        attempts.onMediaFailure(wasReady = false)
        assertEquals(4_500L, attempts.remaining())
        now += 5_000_000_000L
        attempts.onMediaFailure(wasReady = false)
        assertEquals(0L, attempts.remaining())
    }

    @Test fun failureExclusionIsPerQualityAndRespectsOriginalBudget() {
        var clock = 0L
        val attempts = PlaybackSourceAttempts { clock }
        attempts.reject(AudioQuality.HIGH, "winner-with-bad-media")
        assertEquals(setOf("winner-with-bad-media"), attempts.exclusions(AudioQuality.HIGH))
        assertTrue(attempts.exclusions(AudioQuality.LOSSLESS).isEmpty())
        clock = 4_000_000_000L
        assertEquals(500L, attempts.remaining())
        clock += 500_000_000L
        assertEquals(0L, attempts.remaining())
        attempts.startBudget()
        assertEquals(4500L, attempts.remaining())
        assertTrue("winner-with-bad-media" in attempts.exclusions(AudioQuality.HIGH))
        attempts.reset()
        assertTrue(attempts.exclusions(AudioQuality.HIGH).isEmpty())
    }

    @Test fun noResourceAndPolicyErrorsDoNotTripGlobalCircuit() {
        var clock = 0L
        val health = SourceHealth { clock }
        assertFalse(isTransportFailure(IllegalStateException("no recording")))
        assertFalse(isTransportFailure(SourceHttpException(404)))
        assertFalse(isTransportFailure(SourcePolicyException("legacy HTTP disabled")))
        assertTrue(isTransportFailure(SourceHttpException(503)))
        assertTrue(isTransportFailure(IOException("network")))
        repeat(3) { health.recordTransportFailure("unstable") }
        assertTrue(health.isCoolingDown("unstable"))
        clock = 60_000_000_000L
        assertFalse(health.isCoolingDown("unstable"))
        health.recordTransportFailure("unstable")
        assertFalse(health.isCoolingDown("unstable"))
    }

    @Test fun miguColdResolutionUsesStableIdRatherThanOldUrlOrMatchingTitle() = runBlocking {
        val saved = Song("track-2", "Song", "Artist", "", source = "mg", songmid = "https://expired.test/old")
        assertEquals("track-2", saved.stableProviderId)
        val result = refreshMiguUrl(saved) {
            assertEquals("Song Artist", it)
            listOf(saved.copy(id = "track-1", playbackHintUrl = "https://audio.test/wrong"),
                saved.copy(playbackHintUrl = "https://audio.test/refresh"))
        }
        assertEquals("https://audio.test/refresh", result)
    }

    @Test fun expiredMiguHintIsSkippedInSearchResults() = runBlocking {
        val expired = "https://audio.example/expired"
        val fresh = "https://audio.example/fresh"
        val song = Song("123", "Track", "Artist", "Album", source = "mg", playbackHintUrl = expired)
        assertEquals(fresh, refreshMiguUrl(song, expired) {
            listOf(song.copy(id = "other", playbackHintUrl = "https://audio.example/other"),
                song.copy(playbackHintUrl = fresh))
        })
        assertNull(refreshMiguUrl(song, expired) { listOf(song) })
    }
}
