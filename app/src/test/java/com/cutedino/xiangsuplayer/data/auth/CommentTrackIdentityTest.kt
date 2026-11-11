package com.cutedino.xiangsuplayer.data.auth

import com.cutedino.xiangsuplayer.core.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentTrackIdentityTest {
    @Test fun numericForeignIdsRequireVerifiedMatch() = runBlocking {
        val song = Song("42", "Song", "Artist", "", durationMs = 180_000L, source = "kw")
        assertNull(resolveNeteaseCommentTrack(song) {
            listOf(song.copy(source = "wy", title = "Other"), song.copy(source = "wy", durationMs = 95_000L))
        })
        assertEquals("60", resolveNeteaseCommentTrack(song) {
            listOf(song.copy(source = "wy", id = "60"))
        })
        assertEquals("42", resolveNeteaseCommentTrack(song.copy(source = "wy")) { error("Unexpected search") })
        assertNull(resolveNeteaseCommentTrack(song.copy(source = "local:folder")) { error("Unexpected search") })
    }
}
