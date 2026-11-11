package com.cutedino.xiangsuplayer.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.ui.main.TvCompactSongCard
import com.cutedino.xiangsuplayer.ui.player.TvPlayerControlRow
import com.cutedino.xiangsuplayer.ui.player.TvPlayerLayout
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w640dp-h360dp-land")
class TvCompactPlayerTest {
    @get:Rule val rule = createComposeRule()
    @Before fun remote() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }

    @Test fun allFiveControlsRemainVisibleAndReachable() {
        var opened = false
        rule.setContent { PixelPlayTvTheme { TvPlayerLayout(stage = {}, controls = {
            TvPlayerControlRow(false, false, onPrevious = {}, onPlayPause = {}, onNext = {}, onQueue = {}, onMore = { opened = true })
        }) } }
        rule.onNodeWithText("播放").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(3) { rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
        val more = rule.onNodeWithText("更多").assertIsFocused().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val bounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(more.right <= bounds.right && more.bottom <= bounds.bottom)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertTrue(opened) }
    }

    @Test fun duplicateSongOnlyMarksTheCurrentQueueEntry() {
        val song = Song("same", "同一首歌", "歌手", "专辑")
        rule.setContent { PixelPlayTvTheme { Column {
            TvCompactSongCard(song, focusKey = "queue/a", isCurrent = false) {}
            TvCompactSongCard(song, focusKey = "queue/b", isCurrent = true) {}
        } } }
        rule.onAllNodesWithContentDescription("当前曲目").assertCountEquals(1)
    }
}
