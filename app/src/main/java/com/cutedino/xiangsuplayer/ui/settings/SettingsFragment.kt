package com.cutedino.xiangsuplayer.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.cutedino.xiangsuplayer.BuildConfig
import com.cutedino.xiangsuplayer.R
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
        setupAboutDevCards()
        setupDirectionalLocks()
        updateCacheSizeText()

        // Sync Version Name with BuildConfig / gradle
        binding.tvAppVersion.text = "像素播放器 TV v${BuildConfig.VERSION_NAME}"
    }

    private fun setupAboutDevCards() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.cardDev1, 1.02f)
        DPadFocusHelper.setupFocusScale(b.cardDev2, 1.02f)
        DPadFocusHelper.setupFocusScale(b.cardDev3, 1.02f)
        DPadFocusHelper.setupFocusScale(b.cardThanks1, 1.02f)

        DPadFocusHelper.setupFocusScale(b.btnDonateApp)
        b.btnDonateApp.setOnClickListener {
            showDonateDialog()
        }
    }

    private fun showDonateDialog() {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_donate, null)
        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseDonate)
        if (btnClose != null) {
            DPadFocusHelper.setupFocusScale(btnClose)
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()

        btnClose?.post {
            btnClose.requestFocus()
        }
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
            Toast.makeText(context, "已恢复 Bing 每日壁纸 (默认 50% 暗色遮罩)", Toast.LENGTH_SHORT).show()
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

        b.btnOpacity0.setTextColor(if (percent == 0) Color.parseColor("#FFA9F06A") else Color.parseColor("#F8FAFC"))
        b.btnOpacity25.setTextColor(if (percent == 25) Color.parseColor("#FFA9F06A") else Color.parseColor("#F8FAFC"))
        b.btnOpacity50.setTextColor(if (percent == 50) Color.parseColor("#FFA9F06A") else Color.parseColor("#F8FAFC"))
        b.btnOpacity75.setTextColor(if (percent == 75) Color.parseColor("#FFA9F06A") else Color.parseColor("#F8FAFC"))
        b.btnOpacity90.setTextColor(if (percent == 90) Color.parseColor("#FFA9F06A") else Color.parseColor("#F8FAFC"))
    }

    private fun setupSourceOptions() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.rbSourceNetease)
    }

    private fun setupDirectionalLocks() {
        val b = _binding ?: return
        val categoryTabs = listOf(
            b.btnTabAudio,
            b.btnTabSource,
            b.btnTabWallpaper,
            b.btnTabCache,
            b.btnTabAbout
        )

        for (btn in categoryTabs) {
            btn.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (btn == b.btnTabAudio) {
                        onReturnToTopNav?.invoke()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private fun setupCategoryTabs() {
        val b = _binding ?: return
        val tabs = listOf(
            b.btnTabAudio,
            b.btnTabSource,
            b.btnTabWallpaper,
            b.btnTabCache,
            b.btnTabAbout
        )
        val panels = listOf(
            b.panelAudio,
            b.panelSource,
            b.panelWallpaper,
            b.panelCache,
            b.panelAbout
        )

        for (i in tabs.indices) {
            DPadFocusHelper.setupFocusScale(tabs[i])
            tabs[i].setOnClickListener {
                switchTab(i, tabs, panels)
            }
        }
    }

    private fun switchTab(index: Int, tabs: List<View>, panels: List<View>) {
        for (i in tabs.indices) {
            if (i == index) {
                (tabs[i] as? android.widget.Button)?.setTextColor(Color.parseColor("#FFA9F06A"))
                panels[i].visibility = View.VISIBLE
            } else {
                (tabs[i] as? android.widget.Button)?.setTextColor(Color.parseColor("#94A3B8"))
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
