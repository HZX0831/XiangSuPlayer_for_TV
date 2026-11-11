package com.cutedino.xiangsuplayer.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.cutedino.xiangsuplayer.ui.components.TvReadingText

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import com.cutedino.xiangsuplayer.ui.components.TvTextInput
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import com.cutedino.xiangsuplayer.ui.theme.tvWindowSpec
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land")
class TvSharedComponentTest {
    @get:Rule val rule = createComposeRule()
    @Before fun remote() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }

    @Test fun primaryIsNotSelectedAndBusyRetainsFocusWithoutExecuting() {
        var busy by mutableStateOf(false)
        var clicks = 0
        rule.setContent { PixelPlayTvTheme { TvTextAction("播放", primary = true, busy = busy) { clicks++ } } }
        val action = rule.onNodeWithText("播放", substring = true)
        action.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        action.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        rule.runOnIdle { busy = true }
        action.assertIsFocused().assertIsNotEnabled()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(0, clicks); busy = false }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun navigatingToInputDoesNotCreateAnEditorUntilConfirm() {
        rule.setContent { PixelPlayTvTheme { Column { TvTextInput("", {}, "音源地址", Modifier) } } }
        rule.onNodeWithContentDescription("音源地址").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test fun windowSpecUsesLogicalSizeAndFontScale() {
        assertFalse(tvWindowSpec(960, 540, 1f).compact)
        assertTrue(tvWindowSpec(640, 360, 1f).compact)
        assertTrue(tvWindowSpec(960, 540, 1.3f).compact)
    }
    @Test fun systemMotionScaleUpdatesWithoutRecreatingTheScreen() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        android.provider.Settings.Global.putFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var observed = -1f
        rule.setContent { PixelPlayTvTheme { observed = com.cutedino.xiangsuplayer.ui.theme.LocalTvMotionScale.current } }
        rule.runOnIdle { assertEquals(0f, observed, 0f) }
        android.provider.Settings.Global.putFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.runOnIdle { assertEquals(0.5f, observed, 0f) }
        android.provider.Settings.Global.putFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test fun longReadOnlyTextScrollsWithRemoteKeys() {
        val text = (1..30).joinToString("\n") { "第 $it 行" }
        rule.setContent { PixelPlayTvTheme { TvReadingText(text, Modifier.height(100.dp)) } }
        val reader = rule.onNodeWithText(text)
        reader.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.runOnIdle { assertTrue(reader.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f) }
    }

}
