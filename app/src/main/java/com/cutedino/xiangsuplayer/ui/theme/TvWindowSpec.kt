package com.cutedino.xiangsuplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Window-level metrics, shared by Activities and native render fixtures. */
internal data class TvWindowSpec(val compact: Boolean) {
    val horizontalPadding get() = if (compact) 32.dp else 48.dp
    val verticalPadding get() = if (compact) 18.dp else 27.dp
    val railWidth get() = if (compact) 112.dp else 136.dp
    val gutter get() = if (compact) 16.dp else 24.dp
}

internal fun tvWindowSpec(widthDp: Int, heightDp: Int, fontScale: Float) =
    TvWindowSpec(widthDp < 800 || heightDp < 450 || fontScale >= 1.2f)

@Composable
internal fun rememberTvWindowSpec(): TvWindowSpec {
    val config = LocalConfiguration.current
    return tvWindowSpec(config.screenWidthDp, config.screenHeightDp, LocalDensity.current.fontScale)
}
