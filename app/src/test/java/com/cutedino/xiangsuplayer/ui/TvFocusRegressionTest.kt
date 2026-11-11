package com.cutedino.xiangsuplayer.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performClick
import androidx.compose.ui.focus.FocusRequester
import androidx.test.platform.app.InstrumentationRegistry
import com.cutedino.xiangsuplayer.ui.components.TvWavyMusicSlider
import com.cutedino.xiangsuplayer.ui.main.TvToplistScreen
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D-pad focus regressions for the TV UI. Uses a plain Application so the real App (ExoPlayer,
 * network, DB init) does not run under Robolectric.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class TvFocusRegressionTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun nonTouchMode() {
        // TV remotes run in non-touch mode; clickable() only takes focus there.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun pill(label: String) = rule.onNode(hasText(label) and hasClickAction())

    @Test
    fun toplistLeftExitAfterPlatformSwitchDoesNotCrash() {
        rule.setContent {
            PixelPlayTvTheme {
                TvToplistScreen(fetchBoardSongs = { _, _ -> Result.success(emptyList()) })
            }
        }
        rule.waitForIdle()

        // Remember a WY board, then switch platform so that board's requester is disposed.
        pill("新歌榜").performSemanticsAction(SemanticsActions.RequestFocus)
        pill("QQ音乐").performSemanticsAction(SemanticsActions.RequestFocus)
        pill("QQ音乐").performClick()
        rule.waitForIdle()

        // Into the detail pane, then Left out of it. This used to throw
        // IllegalStateException: FocusRequester is not initialized.
        pill("刷新").performSemanticsAction(SemanticsActions.RequestFocus)
        pill("刷新").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()

        // Lands on the selected (first) QQ board.
        pill("热歌榜").assertIsFocused()
    }

    @Test
    fun sliderKeepsFocusWhenSeekingBecomesUnavailable() {
        var canSeek by mutableStateOf(true)
        rule.setContent {
            PixelPlayTvTheme {
                Column {
                    TvWavyMusicSlider(
                        progress = 0.3f,
                        isPlaying = false,
                        canSeek = canSeek,
                        isAdjusting = false,
                        onToggleAdjust = {},
                        onSeekRelative = {},
                        onExitDown = { false },
                        modifier = Modifier.fillMaxWidth().testTag("slider")
                    )
                }
            }
        }
        rule.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithTag("slider").assertIsFocused()

        // Track change: PlayerController resets duration to 0, so canSeek goes false.
        canSeek = false
        rule.waitForIdle()
        rule.onNodeWithTag("slider").assertIsFocused()
    }

    @Test
    fun tryRequestFocusOnDetachedRequesterReturnsFalse() {
        val detached = FocusRequester()
        rule.setContent { PixelPlayTvTheme { } }
        rule.runOnIdle { assertFalse(detached.tryRequestFocus()) }
    }
}
