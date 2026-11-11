package com.cutedino.xiangsuplayer.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
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
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 48.dp, vertical = 27.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        TvNavigationRail(TvMainTab.EXPLORE, {}, focus)
                        TvHomeContent(song, false, listOf(song, song.copy(id = "review-b", title = "远处的灯", artist = "纸间乐队"),
                            song.copy(id = "review-c", title = "夜航", artist = "北岸二重奏")), {}, {}, {}, Modifier.weight(1f))
                    }
                    PixelPlayTvTheme(song.coverUrl) {
                        TvMiniPlayer(song, false, PlayMode.SEQUENCE, .395f, null, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        rule.onNode(hasText("继续播放") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        capture("home-populated")
    }
    @Test fun playerWithLyricsAndArtwork() {
        instrumentation.setInTouchMode(false)
        val song = fixture()
        rule.setContent {
            PixelPlayTvTheme(song.coverUrl) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 48.dp, vertical = 27.dp)) {
                    TvPlayerStage(song, AudioQuality.LOSSLESS, null, listOf(
                        LyricLine(0, "风从窗边经过"), LyricLine(5000, "把今天轻轻放下"),
                        LyricLine(10000, "远处还有一盏灯"), LyricLine(15000, "等我们慢慢回家")), 1, true, false,
                        Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.fillMaxWidth()) {
                        Text("01:42", style = MaterialTheme.typography.labelMedium)
                        TvWavyMusicSlider(.395f, true, true, false, {}, {}, { false }, Modifier.weight(1f))
                        Text("04:18", style = MaterialTheme.typography.labelMedium)
                    }
                    TvPlayerControlRow(true, false, Modifier.fillMaxWidth(), onPrevious = {}, onPlayPause = {}, onNext = {}, onQueue = {}, onMore = {})
                }
            }
        }
        rule.onNode(hasText("暂停") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        capture("player-lyrics")
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
                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 48.dp, vertical = 27.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    TvNavigationRail(TvMainTab.TOPLIST, {}, focus)
                    Box(Modifier.weight(1f)) {
                        com.cutedino.xiangsuplayer.ui.main.TvToplistScreen(fetchBoardSongs = { _, _ -> Result.success(songs) })
                    }
                }
            }
        }
        rule.waitForIdle()
        capture("toplist-rows")
    }

}
