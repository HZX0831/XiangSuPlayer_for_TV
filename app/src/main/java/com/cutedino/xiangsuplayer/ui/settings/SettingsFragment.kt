package com.cutedino.xiangsuplayer.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.databinding.FragmentSettingsBinding
import com.cutedino.xiangsuplayer.ui.main.MainActivity

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    var onReturnToTopNav: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategoryTabs()
        setupQualityOptions()
        setupSourceOptions()
        setupWallpaperOptions()
        setupCacheActions()
        setupDirectionalLocks()
        updateCacheSizeText()
    }

    private fun setupWallpaperOptions() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.rbBingWallpaper)
        DPadFocusHelper.setupFocusScale(b.rbCustomWallpaper)
        DPadFocusHelper.setupFocusScale(b.btnSaveWallpaper)
        DPadFocusHelper.setupFocusScale(b.btnResetBingWallpaper)

        DPadFocusHelper.setupFocusScale(b.btnOpacity0)
        DPadFocusHelper.setupFocusScale(b.btnOpacity25)
        DPadFocusHelper.setupFocusScale(b.btnOpacity50)
        DPadFocusHelper.setupFocusScale(b.btnOpacity75)
        DPadFocusHelper.setupFocusScale(b.btnOpacity90)

        val customUrl = SessionManager.getWallpaperUrl(requireContext())
        if (customUrl.isNullOrBlank()) {
            b.rbBingWallpaper.isChecked = true
            b.etCustomWallpaperUrl.setText("")
        } else {
            b.rbCustomWallpaper.isChecked = true
            b.etCustomWallpaperUrl.setText(customUrl)
        }

        val currentOpacity = SessionManager.wallpaperOpacityPercent
        updateOpacityUI(currentOpacity)

        b.btnOpacity0.setOnClickListener { updateOpacityAndApply(0) }
        b.btnOpacity25.setOnClickListener { updateOpacityAndApply(25) }
        b.btnOpacity50.setOnClickListener { updateOpacityAndApply(50) }
        b.btnOpacity75.setOnClickListener { updateOpacityAndApply(75) }
        b.btnOpacity90.setOnClickListener { updateOpacityAndApply(90) }

        b.btnSaveWallpaper.setOnClickListener {
            if (b.rbBingWallpaper.isChecked) {
                SessionManager.saveWallpaperUrl(requireContext(), null)
                b.etCustomWallpaperUrl.setText("")
                (activity as? MainActivity)?.loadWallpaper()
                Toast.makeText(context, "已成功切换为 Bing 每日超清壁纸", Toast.LENGTH_SHORT).show()
            } else {
                val inputUrl = b.etCustomWallpaperUrl.text.toString().trim()
                if (inputUrl.isEmpty()) {
                    Toast.makeText(context, "请输入自定义壁纸图片 URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                SessionManager.saveWallpaperUrl(requireContext(), inputUrl)
                (activity as? MainActivity)?.loadWallpaper()
                Toast.makeText(context, "自定义壁纸已应用并保存", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnResetBingWallpaper.setOnClickListener {
            SessionManager.saveWallpaperUrl(requireContext(), null)
            b.rbBingWallpaper.isChecked = true
            b.etCustomWallpaperUrl.setText("")
            updateOpacityAndApply(50)
            (activity as? MainActivity)?.loadWallpaper()
            Toast.makeText(context, "已恢复 Bing 每日壁纸与默认 50% 黑化透明度", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateOpacityAndApply(percent: Int) {
        SessionManager.wallpaperOpacityPercent = percent
        updateOpacityUI(percent)
        (activity as? MainActivity)?.updateWallpaperOverlayOpacity(percent)
    }

    private fun updateOpacityUI(percent: Int) {
        val b = _binding ?: return
        b.tvOpacityInfo.text = "背景暗色遮罩黑化程度：$percent%"

        val buttons = listOf(
            b.btnOpacity0 to 0,
            b.btnOpacity25 to 25,
            b.btnOpacity50 to 50,
            b.btnOpacity75 to 75,
            b.btnOpacity90 to 90
        )

        for ((btn, valP) in buttons) {
            if (valP == percent) {
                btn.setTextColor(Color.parseColor("#FFA9F06A"))
            } else {
                btn.setTextColor(Color.parseColor("#FFFFFF"))
            }
        }
    }

    private fun setupSourceOptions() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.rbSourceNetease)
        b.rbSourceNetease.setOnClickListener {
            b.rbSourceNetease.isChecked = true
            Toast.makeText(context, "当前已选中【网易云音乐】核心原生音源，暂无可切换的其它音源", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDirectionalLocks() {
        val b = _binding ?: return

        b.btnTabAudio.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        onReturnToTopNav?.invoke()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        b.rbLossless.requestFocus()
                        true
                    }
                    else -> false
                }
            } else false
        }

        b.btnTabSource.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                b.rbSourceNetease.requestFocus()
                true
            } else false
        }

        b.btnTabWallpaper.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                b.rbBingWallpaper.requestFocus()
                true
            } else false
        }

        b.btnTabCache.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                b.btnClearCache.requestFocus()
                true
            } else false
        }

        val leftToAudioTabListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                b.btnTabAudio.requestFocus()
                true
            } else false
        }

        b.rbStandard.setOnKeyListener(leftToAudioTabListener)
        b.rbHigh.setOnKeyListener(leftToAudioTabListener)
        b.rbLossless.setOnKeyListener(leftToAudioTabListener)
        b.rbHires.setOnKeyListener(leftToAudioTabListener)
        b.btnSaveAudioSettings.setOnKeyListener(leftToAudioTabListener)

        b.rbSourceNetease.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                b.btnTabSource.requestFocus()
                true
            } else false
        }

        val leftToWallpaperTabListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                b.btnTabWallpaper.requestFocus()
                true
            } else false
        }

        b.rbBingWallpaper.setOnKeyListener(leftToWallpaperTabListener)
        b.rbCustomWallpaper.setOnKeyListener(leftToWallpaperTabListener)
        b.etCustomWallpaperUrl.setOnKeyListener(leftToWallpaperTabListener)
        b.btnOpacity0.setOnKeyListener(leftToWallpaperTabListener)
        b.btnSaveWallpaper.setOnKeyListener(leftToWallpaperTabListener)
        b.btnResetBingWallpaper.setOnKeyListener(leftToWallpaperTabListener)

        b.btnClearCache.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                b.btnTabCache.requestFocus()
                true
            } else false
        }
    }

    private fun setupCategoryTabs() {
        val b = _binding ?: return

        DPadFocusHelper.setupFocusScale(b.btnTabAudio)
        DPadFocusHelper.setupFocusScale(b.btnTabSource)
        DPadFocusHelper.setupFocusScale(b.btnTabWallpaper)
        DPadFocusHelper.setupFocusScale(b.btnTabCache)
        DPadFocusHelper.setupFocusScale(b.btnTabAbout)

        b.btnTabAudio.setOnClickListener { switchPanel(0) }
        b.btnTabSource.setOnClickListener { switchPanel(1) }
        b.btnTabWallpaper.setOnClickListener { switchPanel(2) }
        b.btnTabCache.setOnClickListener { switchPanel(3) }
        b.btnTabAbout.setOnClickListener { switchPanel(4) }

        b.btnTabAudio.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) switchPanel(0) }
        b.btnTabSource.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) switchPanel(1) }
        b.btnTabWallpaper.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) switchPanel(2) }
        b.btnTabCache.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) switchPanel(3) }
        b.btnTabAbout.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) switchPanel(4) }
    }

    private fun switchPanel(index: Int) {
        val b = _binding ?: return
        val tabs = listOf(b.btnTabAudio, b.btnTabSource, b.btnTabWallpaper, b.btnTabCache, b.btnTabAbout)
        val panels = listOf(b.panelAudio, b.panelSource, b.panelWallpaper, b.panelCache, b.panelAbout)

        for (i in tabs.indices) {
            if (i == index) {
                tabs[i].setTextColor(Color.parseColor("#FFA9F06A"))
                panels[i].visibility = View.VISIBLE
            } else {
                tabs[i].setTextColor(Color.parseColor("#94A3B8"))
                panels[i].visibility = View.GONE
            }
        }
    }

    private fun setupQualityOptions() {
        val b = _binding ?: return

        DPadFocusHelper.setupFocusScale(b.rbStandard)
        DPadFocusHelper.setupFocusScale(b.rbHigh)
        DPadFocusHelper.setupFocusScale(b.rbLossless)
        DPadFocusHelper.setupFocusScale(b.rbHires)
        DPadFocusHelper.setupFocusScale(b.btnSaveAudioSettings)

        val currentQ = SessionManager.getAudioQuality(requireContext())
        when (currentQ) {
            AudioQuality.STANDARD -> b.rbStandard.isChecked = true
            AudioQuality.HIGH -> b.rbHigh.isChecked = true
            AudioQuality.LOSSLESS -> b.rbLossless.isChecked = true
            AudioQuality.HIRES -> b.rbHires.isChecked = true
        }

        b.btnSaveAudioSettings.setOnClickListener {
            val selectedQ = when (b.rgQuality.checkedRadioButtonId) {
                b.rbStandard.id -> AudioQuality.STANDARD
                b.rbHigh.id -> AudioQuality.HIGH
                b.rbHires.id -> AudioQuality.HIRES
                else -> AudioQuality.LOSSLESS
            }

            SessionManager.saveAudioQuality(requireContext(), selectedQ)
            PlayerController.setQuality(selectedQ)
            Toast.makeText(context, "音频品质已更新为: ${selectedQ.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCacheActions() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.btnClearCache)

        b.btnClearCache.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            try {
                ctx.cacheDir.deleteRecursively()
                Toast.makeText(ctx, "缓存已成功清理", Toast.LENGTH_SHORT).show()
                updateCacheSizeText()
            } catch (e: Exception) {
                Toast.makeText(ctx, "清理缓存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCacheSizeText() {
        val b = _binding ?: return
        val ctx = context ?: return
        val sizeBytes = getFolderSize(ctx.cacheDir)
        val sizeMb = sizeBytes / (1024.0 * 1024.0)
        b.tvCacheSizeInfo.text = "已占用图片与音频临时缓存：%.2f MB\n格式：图片/直链缓存".format(sizeMb)
    }

    private fun getFolderSize(file: java.io.File?): Long {
        if (file == null || !file.exists()) return 0L
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += if (child.isDirectory) getFolderSize(child) else child.length()
        }
        return size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
