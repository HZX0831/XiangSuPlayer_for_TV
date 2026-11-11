package com.cutedino.xiangsuplayer.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val PixelPlayPurpleDark = Color(0xFF0F1827)
val PixelPlaySurface = Color(0xFF162236)
val PixelPlaySurfaceVariant = Color(0xFF25354A)
val PixelPlayPurplePrimary = Color(0xFFB8D4F5)
val PixelPlayPink = Color(0xFFF6A8C1)
val PixelPlayOrange = Color(0xFFFFBEA3)
val PixelPlayLightPurple = Color(0xFFB8C8DD)
val PixelPlayWhite = Color(0xFFF4F7FC)
val PixelPlayRed = Color(0xFFFFB4A9)
val PixelPlayGreen = Color(0xFFB6DFB9)
val PixelPlayGold = Color(0xFFECD48C)
val BadgeNetease = Color(0xFFFFA8B2)
val BadgeQqMusic = Color(0xFF9EDABD)
val BadgeKugou = Color(0xFFA9C8FF)
val BadgeKuwo = Color(0xFFFFD0A6)
val BadgeMigu = Color(0xFFF6A8C1)
val BadgeLx = Color(0xFFCFC5F3)

internal val LocalTvMotionScale = staticCompositionLocalOf { 1f }
internal data class TvArtworkColors(val background: Color, val primary: Color)
private val artworkColors = LruCache<String, TvArtworkColors>(20)

internal fun readableAccent(candidate: Color, background: Color): Color {
    var color = candidate
    repeat(20) {
        if (ColorUtils.calculateContrast(color.toArgb(), background.toArgb()) >= 4.5) return color
        color = lerp(color, Color.White, 0.16f)
    }
    return Color.White
}

@Composable
fun PixelPlayTvTheme(coverUrl: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var motionScale by remember(context) { mutableFloatStateOf(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        fun refreshMotion() {
            motionScale = runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }.getOrDefault(1f).coerceIn(0f, 10f)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refreshMotion()
        }
        val resume = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshMotion() }
        refreshMotion()
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        lifecycle.addObserver(resume)
        onDispose { context.contentResolver.unregisterContentObserver(observer); lifecycle.removeObserver(resume) }
    }
    var colors by remember { mutableStateOf(TvArtworkColors(PixelPlayPurpleDark, PixelPlayPurplePrimary)) }
    LaunchedEffect(coverUrl) {
        colors = TvArtworkColors(PixelPlayPurpleDark, PixelPlayPurplePrimary)
        if (coverUrl.isNullOrBlank()) return@LaunchedEffect
        artworkColors.get(coverUrl)?.let { colors = it; return@LaunchedEffect }
        try {
            val result = withContext(Dispatchers.IO) {
                val drawable = context.imageLoader.execute(ImageRequest.Builder(context)
                    .data(coverUrl).allowHardware(false).size(128, 128).build()).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                val palette = bitmap?.let { Palette.from(it).maximumColorCount(12).generate() }
                val seed = Color(palette?.getMutedColor(PixelPlayPurpleDark.toArgb()) ?: PixelPlayPurpleDark.toArgb())
                val background = lerp(PixelPlayPurpleDark, seed, 0.16f)
                val accent = Color(palette?.getLightVibrantColor(PixelPlayPurplePrimary.toArgb()) ?: PixelPlayPurplePrimary.toArgb())
                TvArtworkColors(background, readableAccent(accent, background))
            }
            artworkColors.put(coverUrl, result)
            colors = result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the readable brand fallback. */ }
    }
    val background by animateColorAsState(colors.background, tween((450 * motionScale).toInt()), label = "artworkBackground")
    val primary by animateColorAsState(colors.primary, tween((450 * motionScale).toInt()), label = "artworkAccent")
    val onPrimary = if (ColorUtils.calculateContrast(PixelPlayPurpleDark.toArgb(), primary.toArgb()) >= 4.5)
        PixelPlayPurpleDark else Color.Black
    val scheme = darkColorScheme(primary = primary, onPrimary = onPrimary,
        primaryContainer = PixelPlaySurfaceVariant, onPrimaryContainer = PixelPlayWhite,
        secondary = PixelPlayPink, onSecondary = PixelPlayPurpleDark,
        tertiary = PixelPlayOrange, onTertiary = PixelPlayPurpleDark,
        background = background, onBackground = PixelPlayWhite,
        surface = PixelPlaySurface, onSurface = PixelPlayWhite,
        surfaceVariant = PixelPlaySurfaceVariant, onSurfaceVariant = PixelPlayLightPurple,
        outline = Color(0xFF71849B), error = PixelPlayRed, onError = PixelPlayPurpleDark)
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
    CompositionLocalProvider(LocalTvMotionScale provides motionScale) {
        MaterialTheme(colorScheme = scheme,
            shapes = Shapes(small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp)),
            typography = Typography(displayLarge = style(36, 44, FontWeight.Bold), displayMedium = style(32, 40, FontWeight.Bold),
                headlineLarge = style(32, 40, FontWeight.SemiBold), headlineMedium = style(28, 36, FontWeight.SemiBold),
                headlineSmall = style(26, 34, FontWeight.SemiBold), titleLarge = style(24, 32, FontWeight.SemiBold),
                titleMedium = style(20, 28, FontWeight.Medium), titleSmall = style(18, 26, FontWeight.Medium),
                bodyLarge = style(20, 28), bodyMedium = style(18, 26), bodySmall = style(16, 24),
                labelLarge = style(18, 26, FontWeight.Medium), labelMedium = style(16, 24), labelSmall = style(14, 20)),
        ) { CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onBackground, content = content) }
    }
}
