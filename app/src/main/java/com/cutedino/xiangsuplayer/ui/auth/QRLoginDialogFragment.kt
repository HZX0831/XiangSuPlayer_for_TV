package com.cutedino.xiangsuplayer.ui.auth

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.databinding.DialogQrLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi

class QRLoginDialogFragment : DialogFragment() {

    private var _binding: DialogQrLoginBinding? = null
    private val binding get() = _binding!!
    private var currentKey: String? = null
    var onLoginSuccess: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQrLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.btnRefreshQr)
        b.btnRefreshQr.setOnClickListener {
            loadQrCode()
        }

        loadQrCode()
    }

    private fun loadQrCode() {
        val b = _binding ?: return
        b.tvQrStatus.text = "正在生成 Base64 二维码..."
        lifecycleScope.launch {
            val prepRes = NcmApi.qrLoginPrepare()
            val qrResult = prepRes.getOrNull()
            val bSub = _binding ?: return@launch
            if (qrResult != null) {
                currentKey = qrResult.key
                val base64Img = qrResult.qrPngBase64.substringAfter("base64,")
                val bytes = Base64.decode(base64Img, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bSub.ivQrCode.setImageBitmap(bmp)
                bSub.tvQrStatus.text = "请使用手机网易云 App 扫描二维码"

                startPolling(qrResult.key)
            } else {
                bSub.tvQrStatus.text = "二维码生成失败，请按按键刷新"
            }
        }
    }

    private fun startPolling(key: String) {
        lifecycleScope.launch {
            val awaitRes = NcmApi.qrLoginAwait(key) { code, _ ->
                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    when (code) {
                        801 -> b.tvQrStatus.text = "等待扫码..."
                        802 -> b.tvQrStatus.text = "已扫码，请在手机上点击确认授权"
                        803 -> b.tvQrStatus.text = "登录成功！"
                        800 -> b.tvQrStatus.text = "二维码已过期，请刷新"
                    }
                }
            }
            if (awaitRes.isSuccess) {
                val ctx = context
                if (ctx != null) {
                    Toast.makeText(ctx, "网易云账号登录成功！", Toast.LENGTH_SHORT).show()
                }
                val cb = onLoginSuccess
                onLoginSuccess = null
                cb?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
