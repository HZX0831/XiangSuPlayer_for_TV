package com.cutedino.xiangsuplayer.ui

import androidx.compose.ui.semantics.SemanticsProperties

import com.cutedino.xiangsuplayer.ui.player.TvKaraokeScreen

import com.cutedino.xiangsuplayer.ui.player.TvPlayerLayout
import com.cutedino.xiangsuplayer.ui.main.TvSettingsScreen
import com.cutedino.xiangsuplayer.ui.main.TvSourceMarketScreen
import com.cutedino.xiangsuplayer.core.source.market.MarketRelease
import androidx.compose.ui.platform.testTag
import org.junit.Assert.assertTrue

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.model.*
import com.cutedino.xiangsuplayer.ui.components.*
import com.cutedino.xiangsuplayer.ui.main.TvHomeContent
import com.cutedino.xiangsuplayer.ui.player.TvPlayerControlRow
import com.cutedino.xiangsuplayer.ui.player.TvPlayerStage
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native render fixtures. These are synthetic songs, not live-service playback evidence. */
@RunWith(AndroidJUnit4::class)
class TvBrandScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun fixture(): Song {
        val file = File(instrumentation.targetContext.cacheDir, "review-artwork.png")
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xffdfa267.toInt(); canvas.drawRect(0f, 0f, 512f, 280f, paint)
        paint.color = 0xff3f625e.toInt(); canvas.drawRect(0f, 280f, 512f, 512f, paint)
        paint.color = 0xffd6cfb5.toInt()
        canvas.drawPath(Path().apply { moveTo(0f, 292f); lineTo(140f, 274f); lineTo(270f, 330f); lineTo(512f, 275f)
            lineTo(512f, 340f); lineTo(270f, 380f); lineTo(140f, 338f); lineTo(0f, 352f); close() }, paint)
        paint.color = 0xff26312c.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 64f
        canvas.drawPath(Path().apply { moveTo(380f, 530f); lineTo(320f, 422f); lineTo(250f, 382f); lineTo(266f, 320f); lineTo(300f, 250f) }, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return Song("review-a", "沿海公路", "林间来信", "南方回声", file.toURI().toString(), 258000L, "local:review")
    }
    private fun capture(name: String) {
        rule.waitForIdle(); SystemClock.sleep(1800)
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "design-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, name + (InstrumentationRegistry.getArguments().getString("reviewSuffix") ?: "") + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun homeWithARealisticQueue() {
        instrumentation.setInTouchMode(false)
        val song = fixture()
        rule.setContent {
            PixelPlayTvTheme {
                val focus = remember { FocusRequester() }
                TvBrowseFrame(modifier = Modifier.background(MaterialTheme.colorScheme.background), navigation = { TvNavigationRail(TvMainTab.EXPLORE, {}, focus) }, content = {
                    TvHomeContent(song, false, listOf(song, song.copy(id = "review-b", title = "远处的灯", artist = "纸间乐队"),
                        song.copy(id = "review-c", title = "夜航", artist = "北岸二重奏")), {}, {}, {})
                }, miniPlayer = { PixelPlayTvTheme(song.coverUrl) {
                    TvMiniPlayer(song, false, PlayMode.SEQUENCE, .395f, null, {}, {}, {}, {}, {}, Modifier.testTag("mini-player"))
                } })
            }
        }
        rule.onNode(hasText("继续播放") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        val badge = rule.onNodeWithText("本地目录").getUnclippedBoundsInRoot()
        val stage = rule.onNodeWithTag("home-stage").getUnclippedBoundsInRoot()
        assertTrue("Artwork source label is clipped", badge.bottom <= stage.bottom)
        capture("home-populated")
        rule.onNodeWithText("首页").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(4) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN) }
        fullyVisible(rule.onNode(hasText("更多") and hasClickAction()))
        val more = rule.onNodeWithText("更多").fetchSemanticsNode().boundsInRoot
        val mini = rule.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Navigation overlaps MiniPlayer", more.bottom <= mini.top)
        capture("home-more-focus")
    }
    @Test fun playerWithLyricsAndArtwork() {
        instrumentation.setInTouchMode(false)
        val song = fixture()
        rule.setContent {
            PixelPlayTvTheme(song.coverUrl) {
                TvPlayerLayout(modifier = Modifier.background(MaterialTheme.colorScheme.background), stage = { stage ->
                    TvPlayerStage(song, AudioQuality.LOSSLESS, null, listOf(
                        LyricLine(0, "风从窗边经过"), LyricLine(5000, "把今天轻轻放下"),
                        LyricLine(10000, "远处还有一盏灯"), LyricLine(15000, "等我们慢慢回家")), 1, true, false, stage)
                }, controls = {
                    Row(Modifier.fillMaxWidth()) {
                        Text("01:42", style = MaterialTheme.typography.labelMedium)
                        TvWavyMusicSlider(.395f, true, true, false, {}, {}, { false }, Modifier.weight(1f))
                        Text("04:18", style = MaterialTheme.typography.labelMedium)
                    }
                    TvPlayerControlRow(true, false, Modifier.fillMaxWidth(), onPrevious = {}, onPlayPause = {}, onNext = {}, onQueue = {}, onMore = {})
                })
            }
        }
        val pause = rule.onNode(hasText("暂停") and hasClickAction())
        pause.performSemanticsAction(SemanticsActions.RequestFocus)
        pause.assertIsFocused()
        repeat(2) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT) }
        rule.onNode(hasText("队列") and hasClickAction()).assertIsFocused()
        repeat(2) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT) }
        pause.assertIsFocused()
        capture("player-lyrics")
        repeat(3) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT) }
        fullyVisible(rule.onNode(hasText("更多") and hasClickAction()))
    }
    @Test fun toplistUsesReadableRowsWithLongTitles() {
        instrumentation.setInTouchMode(false)
        val song = fixture()
        val songs = listOf(song.copy(title = "沿海公路：在很长的夜里慢慢回家"),
            song.copy(id = "review-b", title = "远处的灯", artist = "纸间乐队"),
            song.copy(id = "review-c", title = "The Quiet Road Home", artist = "North Coast Duo"))
        rule.setContent {
            PixelPlayTvTheme {
                val focus = remember { FocusRequester() }
                TvBrowseFrame(modifier = Modifier.background(MaterialTheme.colorScheme.background), navigation = { TvNavigationRail(TvMainTab.TOPLIST, {}, focus) }, content = {
                    com.cutedino.xiangsuplayer.ui.main.TvToplistScreen(fetchBoardSongs = { _, _ -> Result.success(songs) })
                }, miniPlayer = {})
            }
        }
        rule.onNode(hasText("飙升榜") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.onNodeWithText("沿海公路：在很长的夜里慢慢回家").assertIsDisplayed()
        capture("toplist-rows")
    }

    private fun fullyVisible(node: SemanticsNodeInteraction) {
        node.assertIsFocused().assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Focused target is clipped: $bounds / $screen", bounds.left >= screen.left && bounds.top >= screen.top && bounds.right <= screen.right && bounds.bottom <= screen.bottom)
    }

    @Test fun settingsHaveReachableAdvancedControls() {
        instrumentation.setInTouchMode(false)
        rule.setContent { PixelPlayTvTheme {
            val focus = remember { FocusRequester() }
            TvBrowseFrame(modifier = Modifier.background(MaterialTheme.colorScheme.background), navigation = { TvNavigationRail(TvMainTab.MORE, {}, focus) },
                content = { TvSettingsScreen({}) }, miniPlayer = {})
        } }
        capture("settings-playback")
        val first = rule.onNodeWithText(AudioQuality.entries.first().displayName)
        first.performSemanticsAction(SemanticsActions.RequestFocus)
        val compatibility = hasText("旧版 HTTP 音源", substring = true) and hasClickAction()
        var reached = false
        repeat(16) {
            if (!reached) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                rule.waitForIdle()
                reached = rule.onAllNodes(compatibility).fetchSemanticsNodes().any { it.config.contains(SemanticsProperties.Focused) && it.config[SemanticsProperties.Focused] }
            }
        }
        fullyVisible(rule.onNode(compatibility))
        capture("settings-compatibility")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        fullyVisible(rule.onNode(hasText("主项目作者", substring = true)))
        capture("settings-about")
    }

    @Test fun marketFailureAndEmptyHaveDifferentStates() {
        instrumentation.setInTouchMode(false)
        var calls = 0
        rule.setContent { PixelPlayTvTheme {
            val focus = remember { FocusRequester() }
            TvBrowseFrame(modifier = Modifier.background(MaterialTheme.colorScheme.background), navigation = { TvNavigationRail(TvMainTab.MORE, {}, focus) }, content = {
                TvSourceMarketScreen(fetchReleases = { if (++calls == 1) Result.failure(IllegalStateException("offline")) else Result.success(emptyList()) })
            }, miniPlayer = {})
        } }
        val context = instrumentation.targetContext
        rule.onNodeWithText(context.getString(com.cutedino.xiangsuplayer.R.string.tv_load_failed)).assertIsDisplayed()
        capture("market-error")
        rule.onNodeWithText(context.getString(com.cutedino.xiangsuplayer.R.string.tv_retry)).performClick()
        rule.onNodeWithText(context.getString(com.cutedino.xiangsuplayer.R.string.tv_market_empty)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(com.cutedino.xiangsuplayer.R.string.tv_load_failed)).assertDoesNotExist()
    }

    @Test fun emptyHomeWithoutMiniPlayerHasAReachableSearchAction() {
        instrumentation.setInTouchMode(false)
        var destination: TvMainTab? = null
        rule.setContent { PixelPlayTvTheme {
            val focus = remember { FocusRequester() }
            TvBrowseFrame(modifier = Modifier.background(MaterialTheme.colorScheme.background),
                navigation = { TvNavigationRail(TvMainTab.EXPLORE, {}, focus) },
                content = { TvHomeContent(null, false, emptyList(), {}, {}, { destination = it }) }, miniPlayer = {})
        } }
        val find = rule.onNode(hasText("找首歌") and hasClickAction())
        find.performSemanticsAction(SemanticsActions.RequestFocus)
        fullyVisible(find)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.runOnIdle { org.junit.Assert.assertEquals(TvMainTab.SEARCH, destination) }
        capture("home-empty")
    }

    @Test fun pureLyricsKeepLongOriginalAndTranslationReadable() {
        val original = "把今天轻轻放下，沿着很长的海岸慢慢回家"
        rule.setContent { PixelPlayTvTheme {
            TvPlayerLayout(modifier = Modifier.background(MaterialTheme.colorScheme.background), stage = {
                TvPlayerStage(fixture().copy(coverUrl = ""), AudioQuality.LOSSLESS, null,
                    listOf(LyricLine(0, original, translation = "Let the day settle, and follow the coast home.")), 0, true, true, it)
            }, controls = {})
        } }
        rule.onNodeWithText(original).assertIsDisplayed()
        rule.onNodeWithText("Let the day settle, and follow the coast home.").assertIsDisplayed()
        capture("player-pure-long")
    }

    @Test fun playerWithoutArtworkOrLyricsStillOffersAllControls() {
        instrumentation.setInTouchMode(false)
        rule.setContent { PixelPlayTvTheme {
            TvPlayerLayout(modifier = Modifier.background(MaterialTheme.colorScheme.background), stage = {
                TvPlayerStage(fixture().copy(coverUrl = "", title = "一首有很长标题的歌曲：沿海公路和远处的灯"), AudioQuality.LOSSLESS, null,
                    emptyList(), -1, true, false, it)
            }, controls = { TvPlayerControlRow(false, false, Modifier.fillMaxWidth(), onPrevious = {}, onPlayPause = {}, onNext = {}, onQueue = {}, onMore = {}) })
        } }
        rule.onNodeWithText("播放").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(3) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT) }
        fullyVisible(rule.onNode(hasText("更多") and hasClickAction()))
        capture("player-no-lyrics")
    }

    @Test fun karaokeKeepsExitReachableOnCompactScreens() {
        instrumentation.setInTouchMode(false)
        var closed = false
        rule.setContent { PixelPlayTvTheme { TvKaraokeScreen { closed = true } } }
        rule.onNodeWithText(instrumentation.targetContext.getString(com.cutedino.xiangsuplayer.R.string.tv_point_enter)).performClick()
        rule.onNodeWithText(instrumentation.targetContext.getString(com.cutedino.xiangsuplayer.R.string.tv_phone_order))
            .performSemanticsAction(SemanticsActions.RequestFocus)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        val exit = rule.onNode(hasText(instrumentation.targetContext.getString(com.cutedino.xiangsuplayer.R.string.tv_karaoke_exit)) and hasClickAction())
        fullyVisible(exit)
        capture("karaoke-empty")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.runOnIdle { assertTrue(closed) }
    }

    @Test fun failedInstallationKeepsTheReasonAndReturnsFocusToItsAsset() {
        instrumentation.setInTouchMode(false)
        val asset = com.cutedino.xiangsuplayer.core.source.market.MarketAsset(1, "review.zip", 1024, "https://example.invalid/review.zip")
        rule.setContent { PixelPlayTvTheme {
            CompositionLocalProvider(com.cutedino.xiangsuplayer.ui.theme.LocalTvFocusMemory provides remember { com.cutedino.xiangsuplayer.ui.theme.TvFocusMemory() }) {
            TvBrowseFrame(modifier = Modifier.background(MaterialTheme.colorScheme.background), navigation = {
                TvNavigationRail(TvMainTab.MORE, {}, remember { FocusRequester() })
            }, content = {
                TvSourceMarketScreen(fetchReleases = { Result.success(listOf(MarketRelease(1, "v1", "测试版本", "2026-11-10", "", listOf(asset)))) },
                    installAsset = { _, _ -> Result.failure(IllegalStateException("下载失败：请检查网络后重试")) })
            }, miniPlayer = {})
        } } }
        val install = rule.onNode(hasText("review.zip", substring = true) and hasClickAction())
        install.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.onNodeWithText("下载失败：请检查网络后重试").assertIsDisplayed()
        capture("market-install-error")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        install.assertIsFocused().assertIsDisplayed()
    }

}
