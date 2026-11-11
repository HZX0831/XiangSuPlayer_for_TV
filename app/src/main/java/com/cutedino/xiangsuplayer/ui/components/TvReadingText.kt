package com.cutedino.xiangsuplayer.ui.components

import androidx.compose.foundation.layout.padding

import android.view.KeyEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import kotlinx.coroutines.launch

/** Read-only text can be scrolled with Up/Down; the boundary returns to normal focus search. */
@Composable
internal fun TvReadingText(text: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 64.dp.toPx() }
    Text(text, modifier.tvFocusableCard(onClick = null).onKeyEvent {
        val key = it.nativeKeyEvent
        if (key.action != KeyEvent.ACTION_DOWN) false
        else when {
            key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && scroll.canScrollForward -> {
                scope.launch { scroll.scrollBy(step) }; true
            }
            key.keyCode == KeyEvent.KEYCODE_DPAD_UP && scroll.canScrollBackward -> {
                scope.launch { scroll.scrollBy(-step) }; true
            }
            else -> false
        }
    }.verticalScroll(scroll).padding(8.dp), style = MaterialTheme.typography.bodyMedium)
}
