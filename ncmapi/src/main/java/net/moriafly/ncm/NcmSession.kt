@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package net.moriafly.ncm

import android.content.Context
import android.content.SharedPreferences

/**
 * 网易云 Session / Cookie 存储
 */
class NcmSession internal constructor(
    private val sp: SharedPreferences,
) {
    companion object {
        private const val PREFS_NAME = "ncm_session"

        fun install(appContext: Context): NcmSession {
            val sp = appContext.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val instance = NcmSession(sp)
            INSTANCE = instance
            return instance
        }

        @Volatile
        internal var INSTANCE: NcmSession? = null
            private set

        fun requireInstance(): NcmSession = INSTANCE
            ?: error("NcmSession not installed! Please call NcmSession.install(context) first")
    }

    var cookies: MutableMap<String, String>
        get() {
            val raw = sp.getString("cookies", "")?.takeIf { it.isNotBlank() }
                ?: return LinkedHashMap()
            val m = LinkedHashMap<String, String>()
            raw.split('&').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) m[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
            return m
        }
        private set(value) {
            val s = value.entries.joinToString("&") { (k, v) -> "$k=$v" }
            sp.edit().putString("cookies", s).apply()
        }

    var os: String
        get() = sp.getString("os", "pc") ?: "pc"
        set(value) { sp.edit().putString("os", value).apply() }

    var realIp: String?
        get() = sp.getString("realIp", null)?.takeIf { it.isNotBlank() }
        set(value) { sp.edit().putString("realIp", value).apply() }

    var proxy: String?
        get() = sp.getString("proxy", null)?.takeIf { it.isNotBlank() }
        set(value) { sp.edit().putString("proxy", value).apply() }

    var userId: Long
        get() = sp.getLong("user_id", 0L)
        set(value) { sp.edit().putLong("user_id", value).apply() }

    var nickname: String
        get() = sp.getString("user_nickname", "") ?: ""
        set(value) { sp.edit().putString("user_nickname", value).apply() }

    val isLogin: Boolean get() = cookies["MUSIC_U"]?.isNotBlank() == true

    fun merge(rawSetCookies: List<String>) {
        val cur = cookies
        for (raw in rawSetCookies) {
            val head = raw.split(';').firstOrNull()?.trim() ?: continue
            val idx = head.indexOf('=')
            if (idx <= 0) continue
            val k = head.substring(0, idx)
            val v = head.substring(idx + 1)
            if (v.isBlank()) {
                cur.remove(k)
            } else cur[k] = v
        }
        cookies = cur
    }

    fun merge(map: Map<String, String?>) {
        val cur = cookies
        for ((k, v) in map) {
            if (v == null || v.isBlank()) cur.remove(k)
            else cur[k] = v
        }
        cookies = cur
    }

    fun toCookieHeader(): String {
        val c = cookies
        if (c.isEmpty()) return ""
        return c.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    fun set(key: String, value: String?) {
        val cur = cookies
        if (value == null || value.isBlank()) cur.remove(key)
        else cur[key] = value
        cookies = cur
    }

    fun clear() {
        sp.edit().remove("cookies").remove("user_id").remove("user_nickname").apply()
    }

    fun logout() {
        val cur = cookies
        cur.remove("MUSIC_U")
        cur.remove("MUSIC_A")
        cur.remove("__csrf")
        cookies = cur
        userId = 0L
        nickname = ""
    }
}
