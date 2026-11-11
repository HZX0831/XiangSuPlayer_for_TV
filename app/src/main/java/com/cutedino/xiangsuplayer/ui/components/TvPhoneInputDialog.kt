package com.cutedino.xiangsuplayer.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.cutedino.xiangsuplayer.ui.theme.rememberTvWindowSpec

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cutedino.xiangsuplayer.core.phone.PhoneChoice
import com.cutedino.xiangsuplayer.core.phone.PhoneInputServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun TvPhoneInputDialog(
    onClose: () -> Unit,
    onInput: (String) -> Unit = {},
    inputReceived: Boolean = false,
    search: (suspend (String) -> List<PhoneChoice>)? = null,
    order: ((PhoneChoice) -> Boolean)? = null
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val close by rememberUpdatedState(onClose)
    val receive by rememberUpdatedState(onInput)
    val enqueue by rememberUpdatedState(order)
    val closeFocus = remember { FocusRequester() }
    var revision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("正在建立配对") }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) close()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(context, revision) {
        val network = withContext(Dispatchers.IO) {
            runCatching { PhoneInputServer.activeNetwork(context) }.getOrNull()
        }
        if (network == null) {
            status = "请连接有私网 IPv4 地址的局域网"
            return@LaunchedEffect
        }
        val server = runCatching {
            PhoneInputServer(
                network.address,
                onInput = { value -> withContext(Dispatchers.Main.immediate) { receive(value) } },
                search = search,
                order = if (order == null) null else { choice ->
                    withContext(Dispatchers.Main.immediate) { enqueue?.invoke(choice) == true }
                }
            )
        }.getOrElse {
            status = "无法启动手机配对，请稍后重试"
            return@LaunchedEffect
        }
        server.use {
            qr = withContext(Dispatchers.Default) { pairingQr(server.clientUrl) }
            status = "请用同一局域网的手机扫码。链接五分钟后失效。"
            withTimeoutOrNull(PhoneInputServer.DURATION_MS) {
                do {
                    delay(750)
                    val current = withContext(Dispatchers.IO) {
                        runCatching { PhoneInputServer.activeNetwork(context) }.getOrNull()
                    }
                } while (current == network)
            }
        }
        qr = null
        status = "配对已结束，请关闭后重新打开"
    }
    val compact = rememberTvWindowSpec().compact
    TvDialogPanel(if (search == null) stringResource(R.string.tv_phone_input) else stringResource(R.string.tv_phone_order), onClose,
        footer = {
            TvTextAction(stringResource(R.string.tv_pair_refresh)) { qr = null; revision++ }
            TvTextAction(stringResource(R.string.tv_close), Modifier.focusRequester(closeFocus), onClick = onClose)
        }) {
        Row(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            qr?.let { Image(it.asImageBitmap(), stringResource(R.string.tv_pairing_qr), Modifier.size(if (compact) 132.dp else 148.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
                if (inputReceived) Text("已收到搜索词，关闭后可编辑或搜索", style = MaterialTheme.typography.bodySmall)
            }
        }
        LaunchedEffect(Unit) { closeFocus.requestFocus() }
    }
}

private fun pairingQr(url: String): Bitmap {
    val size = 288
    val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { index ->
        if (bits[index % size, index / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
