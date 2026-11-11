package com.cutedino.xiangsuplayer.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.ui.components.*
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
class TvCompactNavigationTest {
    @get:Rule val rule = createComposeRule()
    @Before fun remote() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }

    @Test fun lastRailItemScrollsFullyIntoViewAboveMiniPlayerAndOnlyOpensOnConfirm() {
        var selected by mutableStateOf(TvMainTab.EXPLORE)
        rule.setContent { PixelPlayTvTheme {
            val focus = remember { FocusRequester() }
            TvBrowseFrame(navigation = { TvNavigationRail(selected, { selected = it }, focus) }, content = {}, miniPlayer = {
                TvMiniPlayer(Song("x", "测试", "歌手", "专辑"), false, PlayMode.SEQUENCE, 0f, null, {}, {}, {}, {}, {})
            })
        } }
        rule.onNodeWithText("首页").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(4) { rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        val more = rule.onNodeWithText("更多").assertIsFocused().assertIsDisplayed()
        val bounds = more.fetchSemanticsNode().boundsInRoot
        val rootBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= rootBounds.left && bounds.right <= rootBounds.right)
        assertTrue(bounds.top >= rootBounds.top && bounds.bottom <= rootBounds.bottom)
        rule.runOnIdle { assertEquals(TvMainTab.EXPLORE, selected) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(TvMainTab.MORE, selected) }
    }
}
