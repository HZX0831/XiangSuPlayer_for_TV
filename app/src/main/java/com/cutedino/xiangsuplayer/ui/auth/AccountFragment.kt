package com.cutedino.xiangsuplayer.ui.auth

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.databinding.FragmentAccountBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    var onAccountStateChanged: (() -> Unit)? = null
    var onReturnToTopNav: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFocus()
        setupListeners()
        setupUpKeyReturn()
        updateAccountUI()
    }

    private fun setupUpKeyReturn() {
        val b = _binding ?: return
        val upListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onReturnToTopNav?.invoke()
                true
            } else false
        }

        b.btnLogout.setOnKeyListener(upListener)
        b.btnRefreshQr.setOnKeyListener(upListener)
    }

    private fun setupFocus() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.btnLogout)
        DPadFocusHelper.setupFocusScale(b.btnRefreshQr)
    }

    private fun setupListeners() {
        val b = _binding ?: return
        b.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                NcmApi.logout()
                Toast.makeText(context, "已成功退出网易云账号", Toast.LENGTH_SHORT).show()
                onAccountStateChanged?.invoke()
                updateAccountUI()
            }
        }

        b.btnRefreshQr.setOnClickListener {
            loadQrCode()
        }
    }

    fun updateAccountUI() {
        val b = _binding ?: return
        if (NcmApi.isLogin) {
            b.layoutLoggedIn.visibility = View.VISIBLE
            b.layoutLoggedOut.visibility = View.GONE
            b.tvUserNick.text = NcmApi.nickname.ifBlank { "网易云音乐用户" }
            val uid = NcmApi.userId
            b.tvUserId.text = "UID: ${if (uid > 0) uid else "未知"}"

            lifecycleScope.launch {
                val accRes = NcmApi.fetchAndSaveAccountInfo()
                val nick = accRes.getOrNull()?.second
                val subBinding = _binding ?: return@launch
                if (!nick.isNullOrBlank()) {
                    subBinding.tvUserNick.text = nick
                }
                val fetchedUid = NcmApi.userId
                if (fetchedUid > 0) {
                    subBinding.tvUserId.text = "UID: $fetchedUid"
                }
            }
        } else {
            b.layoutLoggedIn.visibility = View.GONE
            b.layoutLoggedOut.visibility = View.VISIBLE
            loadQrCode()
        }
    }

    private fun loadQrCode() {
        val b = _binding ?: return
        b.tvAccountQrStatus.text = "正在生成 Base64 二维码..."
        lifecycleScope.launch {
            val prepRes = NcmApi.qrLoginPrepare()
            val qrResult = prepRes.getOrNull()
            val subBinding = _binding ?: return@launch
            if (qrResult != null) {
                val base64Img = qrResult.qrPngBase64.substringAfter("base64,")
                val bytes = Base64.decode(base64Img, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                subBinding.ivAccountQrCode.setImageBitmap(bmp)
                subBinding.tvAccountQrStatus.text = "请使用手机网易云 App 扫描二维码"

                startPolling(qrResult.key)
            } else {
                subBinding.tvAccountQrStatus.text = "二维码生成失败，请点击刷新"
            }
        }
    }

    private fun startPolling(key: String) {
        lifecycleScope.launch {
            val awaitRes = NcmApi.qrLoginAwait(key) { code, _ ->
                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    when (code) {
                        801 -> b.tvAccountQrStatus.text = "等待扫码..."
                        802 -> b.tvAccountQrStatus.text = "已扫码，请在手机上确认登录"
                        803 -> b.tvAccountQrStatus.text = "登录成功！"
                        800 -> b.tvAccountQrStatus.text = "二维码已过期，请刷新"
                    }
                }
            }
            if (awaitRes.isSuccess) {
                Toast.makeText(context, "网易云账号登录成功！", Toast.LENGTH_SHORT).show()
                onAccountStateChanged?.invoke()
                updateAccountUI()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
