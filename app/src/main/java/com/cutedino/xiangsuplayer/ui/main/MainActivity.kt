package com.cutedino.xiangsuplayer.ui.main

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.cutedino.xiangsuplayer.R
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.databinding.ActivityMainBinding
import com.cutedino.xiangsuplayer.ui.auth.QRLoginDialogFragment
import com.cutedino.xiangsuplayer.ui.explore.ExploreFragment
import com.cutedino.xiangsuplayer.ui.library.LibraryFragment
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity
import com.cutedino.xiangsuplayer.ui.search.SearchFragment
import com.cutedino.xiangsuplayer.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val exploreFragment = ExploreFragment()
    private val searchFragment = SearchFragment()
    private val libraryFragment = LibraryFragment()
    private val historyFragment = HistoryFragment()
    private val settingsFragment = SettingsFragment()

    private var activeTabButton: Button? = null
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadWallpaper()
        updateWallpaperOverlayOpacity()
        setupFragmentCallbacks()
        setupTopNavFocus()
        setupListeners()
        MiniPlayerHelper.setup(this, binding.layoutMiniPlayer)

        activeTabButton = binding.navExplore
        switchFragment(exploreFragment)
        updateTabStyle(binding.navExplore)
        updateLoginStatus()
    }

    fun updateWallpaperOverlayOpacity(percent: Int = SessionManager.wallpaperOpacityPercent) {
        val alpha = (percent / 100.0f).coerceIn(0.0f, 0.95f)
        binding.wallpaperOverlay.alpha = alpha
    }

    fun loadWallpaper() {
        val customUrl = SessionManager.getWallpaperUrl(this)
        if (!customUrl.isNullOrBlank()) {
            binding.ivBingWallpaper.load(customUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_default_wallpaper)
                error(R.drawable.bg_default_wallpaper)
            }
            return
        }

        binding.ivBingWallpaper.setImageResource(R.drawable.bg_default_wallpaper)

        val primaryBingUrl = "https://bing.biturl.top/?resolution=1920&format=image&index=0&mkt=zh-CN"
        binding.ivBingWallpaper.load(primaryBingUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_default_wallpaper)
            error(R.drawable.bg_default_wallpaper)
            listener(
                onError = { _, _ ->
                    val secondaryBingUrl = "https://api.vvhan.com/api/bing"
                    binding.ivBingWallpaper.load(secondaryBingUrl) {
                        crossfade(true)
                        placeholder(R.drawable.bg_default_wallpaper)
                        error(R.drawable.bg_default_wallpaper)
                        listener(
                            onError = { _, _ ->
                                fetchBingJsonWallpaper()
                            }
                        )
                    }
                }
            )
        }
    }

    private fun fetchBingJsonWallpaper() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://bing.biturl.top/?resolution=UHD&format=json&index=0&mkt=zh-CN")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonStr)
                val imgUrl = jsonObj.optString("url")
                if (imgUrl.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        binding.ivBingWallpaper.load(imgUrl) {
                            crossfade(true)
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val url = URL("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonStr)
                val imagesArray = jsonObj.optJSONArray("images")
                if (imagesArray != null && imagesArray.length() > 0) {
                    val imgObj = imagesArray.getJSONObject(0)
                    val relUrl = imgObj.optString("url")
                    val fullUrl = if (relUrl.startsWith("http")) relUrl else "https://cn.bing.com$relUrl"

                    withContext(Dispatchers.Main) {
                        binding.ivBingWallpaper.load(fullUrl) {
                            crossfade(true)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupFragmentCallbacks() {
        exploreFragment.onReturnToTopNav = { binding.navExplore.requestFocus() }
        searchFragment.onReturnToTopNav = { binding.navSearch.requestFocus() }
        libraryFragment.onReturnToTopNav = { binding.navLibrary.requestFocus() }
        historyFragment.onReturnToTopNav = { binding.navHistory.requestFocus() }
        settingsFragment.onReturnToTopNav = { binding.navSettings.requestFocus() }
    }

    private fun setupTopNavFocus() {
        DPadFocusHelper.setupFocusScale(binding.navExplore) { _, hasFocus ->
            if (hasFocus) {
                activeTabButton = binding.navExplore
                updateTabStyle(binding.navExplore)
                switchFragment(exploreFragment)
            }
        }

        DPadFocusHelper.setupFocusScale(binding.navSearch) { _, hasFocus ->
            if (hasFocus) {
                activeTabButton = binding.navSearch
                updateTabStyle(binding.navSearch)
                switchFragment(searchFragment)
            }
        }

        DPadFocusHelper.setupFocusScale(binding.navLibrary) { _, hasFocus ->
            if (hasFocus) {
                activeTabButton = binding.navLibrary
                updateTabStyle(binding.navLibrary)
                switchFragment(libraryFragment)
            }
        }

        DPadFocusHelper.setupFocusScale(binding.navPlayer) { _, hasFocus ->
            binding.navPlayer.text = if (hasFocus) " 播放界面" else ""
            binding.navPlayer.setPadding(if (hasFocus) dpToPx(12) else dpToPx(8), 0, if (hasFocus) dpToPx(12) else dpToPx(8), 0)
        }

        DPadFocusHelper.setupFocusScale(binding.navHistory) { _, hasFocus ->
            binding.navHistory.text = if (hasFocus) " 播放历史" else ""
            binding.navHistory.setPadding(if (hasFocus) dpToPx(12) else dpToPx(8), 0, if (hasFocus) dpToPx(12) else dpToPx(8), 0)
        }

        DPadFocusHelper.setupFocusScale(binding.navLogin) { _, hasFocus ->
            updateLoginStatus(hasFocus)
            binding.navLogin.setPadding(if (hasFocus) dpToPx(12) else dpToPx(8), 0, if (hasFocus) dpToPx(12) else dpToPx(8), 0)
        }

        DPadFocusHelper.setupFocusScale(binding.navSettings) { _, hasFocus ->
            binding.navSettings.text = if (hasFocus) " 设置" else ""
            binding.navSettings.setPadding(if (hasFocus) dpToPx(12) else dpToPx(8), 0, if (hasFocus) dpToPx(12) else dpToPx(8), 0)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupListeners() {
        binding.navExplore.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                exploreFragment.focusFirst()
                true
            } else false
        }

        binding.navSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                searchFragment.focusSearchInput()
                true
            } else false
        }

        binding.navLibrary.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                libraryFragment.focusFirst()
                true
            } else false
        }

        binding.navHistory.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                historyFragment.focusFirst()
                true
            } else false
        }

        binding.navPlayer.setOnClickListener {
            val currentSong = PlayerController.currentSong.value
            if (currentSong != null) {
                val intent = Intent(this, PlayerActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "当前暂无正在播放的歌曲", Toast.LENGTH_SHORT).show()
            }
        }

        binding.navHistory.setOnClickListener {
            activeTabButton = binding.navHistory
            updateTabStyle(binding.navHistory)
            switchFragment(historyFragment)
            historyFragment.loadHistory()
        }

        binding.navLogin.setOnClickListener {
            if (NcmApi.isLogin) {
                showCustomAccountDialog()
            } else {
                val dialog = QRLoginDialogFragment()
                dialog.onLoginSuccess = {
                    updateLoginStatus()
                    exploreFragment.loadRecommendations()
                    libraryFragment.refreshNeteasePlaylists()
                }
                dialog.show(supportFragmentManager, "QRLoginDialog")
            }
        }

        binding.navSettings.setOnClickListener {
            activeTabButton = binding.navSettings
            updateTabStyle(binding.navSettings)
            switchFragment(settingsFragment)
        }
    }

    private fun showCustomAccountDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_info, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvNick = dialogView.findViewById<TextView>(R.id.tvDialogNick)
        val tvUid = dialogView.findViewById<TextView>(R.id.tvDialogUid)
        val btnLogout = dialogView.findViewById<Button>(R.id.btnDialogLogout)
        val btnClose = dialogView.findViewById<Button>(R.id.btnDialogClose)

        tvNick.text = NcmApi.nickname.ifBlank { "网易云用户" }
        val uid = NcmApi.userId
        tvUid.text = "UID: ${if (uid > 0) uid else "未知"}"

        if (btnClose != null) DPadFocusHelper.setupFocusScale(btnClose)
        if (btnLogout != null) DPadFocusHelper.setupFocusScale(btnLogout)

        var canLogout = false
        dialogView.postDelayed({ canLogout = true }, 400)

        btnLogout?.setOnClickListener {
            if (!canLogout) return@setOnClickListener
            lifecycleScope.launch {
                NcmApi.logout()
                updateLoginStatus()
                exploreFragment.loadRecommendations()
                libraryFragment.refreshNeteasePlaylists()
                Toast.makeText(this@MainActivity, "已成功退出网易云账号", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        btnClose?.post {
            btnClose.requestFocus()
        }
    }

    private fun updateTabStyle(selectedButton: Button) {
        val tabs = listOf(binding.navExplore, binding.navSearch, binding.navLibrary, binding.navHistory, binding.navSettings)
        for (tab in tabs) {
            if (tab == selectedButton) {
                tab.setTextColor(Color.parseColor("#FFA9F06A"))
            } else {
                tab.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
    }

    private fun updateLoginStatus(isFocused: Boolean = binding.navLogin.hasFocus()) {
        if (NcmApi.isLogin) {
            val cachedNick = NcmApi.nickname
            binding.navLogin.text = if (isFocused) " ${cachedNick.ifBlank { "已登录" }}" else ""

            lifecycleScope.launch {
                val accRes = NcmApi.fetchAndSaveAccountInfo()
                val nick = accRes.getOrNull()?.second
                val currentFocus = binding.navLogin.hasFocus()
                if (!nick.isNullOrBlank()) {
                    binding.navLogin.text = if (currentFocus) " $nick" else ""
                }
            }
        } else {
            binding.navLogin.text = if (isFocused) " 网易云登录" else ""
        }
    }

    private fun switchFragment(fragment: Fragment) {
        if (currentFragment == fragment && fragment.isAdded) return
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .commit()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
            val focusedView = currentFocus
            if (focusedView != null && isChildOf(focusedView, binding.contentContainer)) {
                (activeTabButton ?: binding.navExplore).requestFocus()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun isChildOf(child: View, parent: View): Boolean {
        var p: Any? = child.parent
        while (p != null) {
            if (p == parent) return true
            p = (p as? View)?.parent
        }
        return false
    }
}
