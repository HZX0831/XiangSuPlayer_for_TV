package com.cutedino.xiangsuplayer.data.auth

import com.cutedino.xiangsuplayer.core.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentIdentityTest {
    @Test fun numericIdsFromOtherPlatformsMustBeMatchedBeforeFetchingNeteaseComments() = runBlocking {
        for (source in listOf("qq", "kg")) {
            val song = Song("12345", "歌名", "歌手", "专辑", durationMs = 180_000, source = source)
            var query = ""
            val id = resolveNeteaseCommentTrack(song) { query = it; listOf(song.copy(id = "67890", source = "wy")) }
            assertEquals("歌名 歌手", query)
            assertEquals("67890", id)
        }
    }

    @Test fun nativeNeteaseIdentityDoesNotNeedAnotherSearch() = runBlocking {
        assertEquals("12345", resolveNeteaseCommentTrack(Song("12345", "歌名", "歌手", "专辑", source = "wy")) {
            error("Native identity must be preserved")
        })
    }
}
