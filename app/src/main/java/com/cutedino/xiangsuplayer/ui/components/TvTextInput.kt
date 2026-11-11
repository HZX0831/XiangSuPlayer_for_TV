package com.cutedino.xiangsuplayer.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard

/** D-pad focus is a navigation state. Only OK enters the native text editor. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun TvTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    onSubmit: (() -> Unit)? = null
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var sawIme by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    val entry = remember { FocusRequester() }
    val editor = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    fun close() { keyboard?.hide(); editing = false; restore = true }
    BackHandler(editing) { close() }
    LaunchedEffect(editing) {
        if (editing) { editor.tryRequestFocus(); keyboard?.show() }
        else { hadFocus = false; sawIme = false }
    }
    LaunchedEffect(editing, imeVisible) {
        if (editing && imeVisible) sawIme = true
        if (editing && sawIme && !imeVisible) close()
    }
    LaunchedEffect(restore) { if (restore) { entry.tryRequestFocus(); restore = false } }
    Box(modifier) {
        if (editing) BasicTextField(
            value, onValueChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { close(); onSubmit?.invoke() }, onSearch = { close(); onSubmit?.invoke() }),
            modifier = Modifier.fillMaxWidth().focusRequester(editor)
                .semantics { contentDescription = label }
                .onPreInterceptKeyBeforeSoftKeyboard {
                    val key = it.nativeKeyEvent
                    if (key.keyCode == KeyEvent.KEYCODE_BACK) {
                        if (key.action == KeyEvent.ACTION_UP) close()
                        true
                    } else false
                }.onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    else if (hadFocus) { editing = false; keyboard?.hide() }
                }.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .heightIn(min = 48.dp).padding(12.dp),
            decorationBox = { field ->
                Box { if (value.isEmpty()) Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium); field() }
            }
        ) else Box(Modifier.fillMaxWidth().focusRequester(entry)
            .semantics { contentDescription = label }
            .tvFocusableCard(shape = RoundedCornerShape(8.dp), onClick = { editing = true })
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(12.dp)) {
            val display = if (value.isBlank()) label else visualTransformation.filter(androidx.compose.ui.text.AnnotatedString(value)).text.text
            Text(display, style = MaterialTheme.typography.bodyMedium,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
