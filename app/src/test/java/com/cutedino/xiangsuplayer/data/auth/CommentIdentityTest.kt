package com.cutedino.xiangsuplayer.data.auth

import com.cutedino.xiangsuplayer.core.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentIdentityTest {
    @Test fun numericIdsFromOtherPlatformsMustBeMatchedBeforeFetchingNeteaseComments() = runBlocking {
        for (source in listOf("qq", "kg")) {
            val song = Song("12345", "歌名", "歌手", "专辑", source = source)
            var query = ""
            val id = resolveNeteaseCommentId(song) { query = it; "67890" }
            assertEquals("歌名 歌手", query)
            assertEquals("67890", id)
        }
    }

    @Test fun nativeNeteaseIdentityDoesNotNeedAnotherSearch() = runBlocking {
        assertEquals("12345", resolveNeteaseCommentId(Song("12345", "歌名", "歌手", "专辑", source = "wy")) {
            error("Native identity must be preserved")
        })
    }
}
