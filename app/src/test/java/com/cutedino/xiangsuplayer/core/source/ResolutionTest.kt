package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.builtin.matchesRecording
import com.cutedino.xiangsuplayer.core.source.market.SourceMarketRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionTest {
    private val full = ResolvedStream("fixture", AudioQuality.HIGH, StreamResult.Full("https://example.test/track"))

    @Test fun fullStreamCancelsSlowRacers() = runBlocking {
        var cancelled = false
        val result = raceStreams(listOf("slow", "fast"), 4_500L) { source ->
            if (source == "fast") full else try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        assertEquals(full, result)
        assertTrue(cancelled)
    }

    @Test fun trialIsRetainedWhenFullStreamTimesOut() = runBlocking {
        val trial = full.copy(result = StreamResult.Trial(full.streamUrl))
        assertEquals(trial, raceStreams(listOf(0, 1), 100L) { if (it == 0) trial else awaitCancellation() })
    }

    @Test fun recordingsMustMatchVersionArtistAndDuration() {
        val song = Song("1", "Track", "Artist", "Album", durationMs = 180_000)
        assertTrue(matchesRecording(song, song.copy(id = "2", title = "Ｔｒａｃｋ", durationMs = 182_000)))
        assertFalse(matchesRecording(song, song.copy(title = "Track (Live)")))
        assertFalse(matchesRecording(song, song.copy(durationMs = 195_000)))
        assertFalse(matchesRecording(song, song.copy(artist = "Someone else")))
    }

    @Test fun differentChineseFileNamesHaveDifferentStableIds() {
        assertNotEquals(SourceMarketRepository.stableId("音源.js"), SourceMarketRepository.stableId("脚本.js"))
    }
}
