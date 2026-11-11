package com.cutedino.xiangsuplayer.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.cutedino.xiangsuplayer.ui.components.TvMainTab
import com.cutedino.xiangsuplayer.ui.components.TvNavigationRail
import com.cutedino.xiangsuplayer.ui.player.TvPlayerControlRow
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land")
class TvBrandFocusTest {
    @get:Rule val rule = createComposeRule()
    @Before fun remoteInput() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun action(text: String) = rule.onNode(hasText(text) and hasClickAction())

    @Test fun railMovesVerticallyAndOnlySelectsOnConfirm() {
        var selected by mutableStateOf(TvMainTab.EXPLORE)
        var selectedCount = 0
        rule.setContent {
            PixelPlayTvTheme {
                val focus = remember { FocusRequester() }
                TvNavigationRail(selected, { selected = it; selectedCount++ }, focus)
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        rule.waitForIdle()
        action("首页").performSemanticsAction(SemanticsActions.RequestFocus)
        action("首页").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        action("搜索").assertIsFocused()
        rule.runOnIdle { assertEquals(TvMainTab.EXPLORE, selected); assertEquals(0, selectedCount) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(TvMainTab.SEARCH, selected); assertEquals(1, selectedCount) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        action("曲库").assertIsFocused()
    }

    @Test fun playerControlRowKeepsTheQueueBetweenNextAndMore() {
        rule.setContent {
            PixelPlayTvTheme {
                TvPlayerControlRow(true, false, onPrevious = {}, onPlayPause = {}, onNext = {}, onQueue = {}, onMore = {})
            }
        }
        action("暂停").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        action(InstrumentationRegistry.getInstrumentation().targetContext.getString(com.cutedino.xiangsuplayer.R.string.tv_next)).assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        action("队列").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        action("更多").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        action("队列").assertIsFocused()
    }
}
