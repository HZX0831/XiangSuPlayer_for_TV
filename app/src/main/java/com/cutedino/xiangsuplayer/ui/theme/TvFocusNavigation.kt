package com.cutedino.xiangsuplayer.ui.theme

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

internal val LocalTvFocusMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

internal class TvFocusMemory {
    val targets = linkedMapOf<FocusRequester, String>()
    var lastFocused: String? = null
    var lastRequester: FocusRequester? = null
    var focusedRequester: FocusRequester? = null
    var scrollToKey: (suspend (String) -> Boolean)? = null
    val hasFocus: Boolean get() = focusedRequester != null
    val needsScroll: Boolean get() = lastFocused != null && preferred(false) == null && scrollToKey != null

    fun preferred(allowFallback: Boolean = true): FocusRequester? =
        lastRequester?.takeIf(targets::containsKey)
            ?: targets.entries.firstOrNull { it.value == lastFocused }?.key
            ?: if (allowFallback) targets.keys.firstOrNull() else null

    fun request(allowFallback: Boolean = true): Boolean {
        val target = preferred(allowFallback) ?: return false
        return target.tryRequestFocus() && focusedRequester === target
    }

    suspend fun restore(): Boolean {
        if (request(false)) return true
        val key = lastFocused
        if (key != null && scrollToKey?.invoke(key) == true) {
            repeat(3) {
                withFrameNanos { }
                if (request(false)) return true
            }
        }
        return request()
    }
}

@Composable
internal fun rememberTvFocusScroller(scroll: suspend (String) -> Boolean) {
    val memory = LocalTvFocusMemory.current ?: return
    val current by rememberUpdatedState(scroll)
    DisposableEffect(memory) {
        val callback: suspend (String) -> Boolean = { current(it) }
        memory.scrollToKey = callback
        onDispose { if (memory.scrollToKey === callback) memory.scrollToKey = null }
    }
}

@Composable
internal fun Modifier.tvFocusTarget(key: String? = null): Modifier {
    val memory = LocalTvFocusMemory.current ?: return this
    val id = key ?: currentCompositeKeyHash.toString()
    val requester = remember(memory, id) { FocusRequester() }
    DisposableEffect(memory, id, requester) {
        memory.targets[requester] = id
        onDispose {
            memory.targets.remove(requester)
            if (memory.focusedRequester === requester) memory.focusedRequester = null
        }
    }
    return focusRequester(requester).onFocusChanged {
        if (it.isFocused) {
            memory.lastFocused = id
            memory.lastRequester = requester
            memory.focusedRequester = requester
        } else if (memory.focusedRequester === requester) {
            memory.focusedRequester = null
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.tvFocusGroup(
    exitTo: (FocusDirection) -> FocusRequester?
): Modifier = focusProperties {
    exit = { exitTo(it) ?: FocusRequester.Default }
}.focusGroup()

/**
 * [FocusRequester.requestFocus] that returns false instead of throwing when the requester's node
 * has already left the composition (Compose 1.6 throws IllegalStateException in that case).
 */
internal fun FocusRequester.tryRequestFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }
