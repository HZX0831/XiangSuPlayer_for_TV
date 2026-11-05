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

    var soundSourceMode: String
        get() = if (::prefs.isInitialized) prefs.getString("sound_source_mode", "auto_race") ?: "auto_race" else "auto_race"
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("sound_source_mode", value).apply() }

    var enabledSoundSources: Set<String>
        get() {
            val defaults = setOf(
                "netease_native",
                "builtin_cross_platform",
                "ikun",
                "xinghai",
                "changqing",
                "nianxin"
            )
            if (!::prefs.isInitialized) return defaults
            val raw = prefs.getString("enabled_sound_sources", null)
            if (raw.isNullOrBlank()) return defaults
            return raw.split(",").filter { it.isNotBlank() }.toSet()
        }
        set(value) {
            if (::prefs.isInitialized) {
                prefs.edit().putString("enabled_sound_sources", value.joinToString(",")).apply()
            }
        }

    // 默认使用主项目 PixelPlay 深蓝紫流体主题 (false)，用户可在设置中开启必应/自定义壁纸 (true)
    var useWallpaperBackground: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean("use_wallpaper_bg", false) else false
        set(value) { if (::prefs.isInitialized) prefs.edit().putBoolean("use_wallpaper_bg", value).apply() }

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

    var wallpaperOpacityPercent: Int
        get() = if (::prefs.isInitialized) prefs.getInt("wallpaper_opacity_percent", 55) else 55
        set(value) { if (::prefs.isInitialized) prefs.edit().putInt("wallpaper_opacity_percent", value).apply() }

    // ── 多平台账号持久化 (QQ音乐 / 酷狗音乐) ──
    var qqMusicUin: String
        get() = if (::prefs.isInitialized) prefs.getString("qq_music_uin", "") ?: "" else ""
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("qq_music_uin", value.trim()).apply() }

    var qqMusicNickname: String
        get() = if (::prefs.isInitialized) prefs.getString("qq_music_nick", "") ?: "" else ""
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("qq_music_nick", value.trim()).apply() }

    var qqMusicCookie: String
        get() = if (::prefs.isInitialized) prefs.getString("qq_music_cookie", "") ?: "" else ""
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("qq_music_cookie", value.trim()).apply() }

    var kugouUserId: String
        get() = if (::prefs.isInitialized) prefs.getString("kugou_userid", "") ?: "" else ""
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("kugou_userid", value.trim()).apply() }

    var kugouNickname: String
        get() = if (::prefs.isInitialized) prefs.getString("kugou_nick", "") ?: "" else ""
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("kugou_nick", value.trim()).apply() }

    var kugouToken: String
        get() = if (::prefs.isInitialized) prefs.getString("kugou_token", "") ?: "" else ""
        set(value) { if (::prefs.isInitialized) prefs.edit().putString("kugou_token", value.trim()).apply() }

    // ── 搜索历史持久化 ──
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
        if (current.size > 12) {
            current.removeAt(current.size - 1)
        }
        prefs.edit().putString("search_history", current.joinToString("|||")).apply()
    }

    fun clearSearchHistory(context: Context) {
        if (!::prefs.isInitialized) init(context)
        prefs.edit().remove("search_history").apply()
    }
}
