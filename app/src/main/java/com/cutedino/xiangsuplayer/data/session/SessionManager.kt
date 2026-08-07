package com.cutedino.xiangsuplayer.data.session

import android.content.Context
import android.content.SharedPreferences
import com.cutedino.xiangsuplayer.core.model.AudioQuality

object SessionManager {
    private const val PREFS_NAME = "tv_player_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var audioQuality: String
        get() = if (::prefs.isInitialized) prefs.getString("audio_quality", "lossless") ?: "lossless" else "lossless"
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("audio_quality", value).apply() }

    fun getAudioQuality(context: Context): AudioQuality {
        if (!::prefs.isInitialized) init(context)
        return when (audioQuality) {
            "standard" -> AudioQuality.STANDARD
            "high" -> AudioQuality.HIGH
            "hires" -> AudioQuality.HIRES
            else -> AudioQuality.LOSSLESS
        }
    }

    fun saveAudioQuality(context: Context, q: AudioQuality) {
        if (!::prefs.isInitialized) init(context)
        audioQuality = when (q) {
            AudioQuality.STANDARD -> "standard"
            AudioQuality.HIGH -> "high"
            AudioQuality.HIRES -> "hires"
            AudioQuality.LOSSLESS -> "lossless"
        }
    }

    var customWallpaperUrl: String?
        get() = if (::prefs.isInitialized) prefs.getString("custom_wallpaper_url", null) else null
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("custom_wallpaper_url", value).apply() }

    fun getWallpaperUrl(context: Context): String? {
        if (!::prefs.isInitialized) init(context)
        return customWallpaperUrl
    }

    fun saveWallpaperUrl(context: Context, url: String?) {
        if (!::prefs.isInitialized) init(context)
        customWallpaperUrl = if (url.isNullOrBlank()) null else url.trim()
    }

    // Wallpaper Overlay Opacity Percentage (0% transparent to 90% dark)
    var wallpaperOpacityPercent: Int
        get() = if (::prefs.isInitialized) prefs.getInt("wallpaper_opacity_percent", 50) else 50
        set(value) { if (::prefs.isInitialized) prefs.edit().putInt("wallpaper_opacity_percent", value).apply() }

    // Search History Storage
    fun getSearchHistory(context: Context): List<String> {
        if (!::prefs.isInitialized) init(context)
        val raw = prefs.getString("search_history", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addSearchHistory(context: Context, query: String) {
        if (!::prefs.isInitialized) init(context)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = getSearchHistory(context).toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > 10) {
            current.removeAt(current.size - 1)
        }
        prefs.edit().putString("search_history", current.joinToString("|||")).apply()
    }

    fun clearSearchHistory(context: Context) {
        if (!::prefs.isInitialized) init(context)
        prefs.edit().remove("search_history").apply()
    }
}
