package com.cutedino.xiangsuplayer.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cutedino.xiangsuplayer.R
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.ui.library.TvPlaylistBatchActions
import com.cutedino.xiangsuplayer.ui.main.TvToplistScreen
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land")
class TvContentRecoveryTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun failedRefreshKeepsSongsAndSuccessfulEmptyResponseClearsThem() {
        val song = Song("a", "保留的歌曲", "歌手", "专辑")
        var calls = 0
        rule.setContent { PixelPlayTvTheme { TvToplistScreen(fetchBoardSongs = { _, _ ->
            when (++calls) {
                1 -> Result.success(listOf(song))
                2 -> Result.failure(IllegalStateException("offline"))
                else -> Result.success(emptyList())
            }
        }) } }
        rule.onNodeWithText(song.title).assertIsDisplayed()
        rule.onNodeWithText("刷新").performClick()
        rule.onNodeWithText(song.title).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.tv_refresh_failed)).assertIsDisplayed()
        rule.onNodeWithText("刷新").performClick()
        rule.onNodeWithText(song.title).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.tv_empty_result)).assertIsDisplayed()
    }

    @Test fun partialPlaylistPlaysOnlyAnIndependentLoadedSnapshot() {
        val songs = mutableListOf(Song("a", "已加载", "歌手", "专辑"))
        var played: List<Song>? = null
        rule.setContent { PixelPlayTvTheme { TvPlaylistBatchActions(songs, 10) { played = it } } }
        rule.onNodeWithText(context.getString(R.string.tv_play_loaded, 1)).performClick()
        songs += Song("b", "之后加载", "歌手", "专辑")
        rule.runOnIdle { assertEquals(listOf("a"), played?.map { it.id }) }
    }
}
