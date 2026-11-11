package net.moriafly.ncm

import android.content.Context
import android.content.SharedPreferences

internal data class NcmSessionSnapshot(
    val generation: Long,
    val cookies: Map<String, String>,
    val os: String,
    val realIp: String?,
    val proxy: String?
) {
    val loggedIn: Boolean get() = !cookies["MUSIC_U"].isNullOrBlank()
}

class NcmSession internal constructor(private val sp: SharedPreferences) {
    private var generation = 0L

    companion object {
        private const val PREFS_NAME = "ncm_session"

        @Synchronized
        fun install(appContext: Context): NcmSession = INSTANCE ?: NcmSession(
            appContext.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ).also { INSTANCE = it }

        @Volatile
        internal var INSTANCE: NcmSession? = null
            private set

        fun requireInstance(): NcmSession = checkNotNull(INSTANCE) { "NcmSession not installed" }
    }

    val cookies: MutableMap<String, String>
        @Synchronized get() {
            val raw = sp.getString("cookies", "").orEmpty()
            if (raw.startsWith('{')) {
                val stored = runCatching { NcmJson.parseAny(raw) as? Map<*, *> }.getOrNull()
                return stored.orEmpty().entries.mapNotNull { (key, value) ->
                    if (key is String && value is String) key to value else null
                }.toMap(linkedMapOf())
            }
            return raw.split('&').mapNotNull(::cookiePair).toMap(linkedMapOf())
        }

    var os: String
        get() = sp.getString("os", "pc") ?: "pc"
        @Synchronized set(value) { sp.edit().putString("os", value).apply() }
    var realIp: String?
        get() = sp.getString("realIp", null)?.takeIf(String::isNotBlank)
        @Synchronized set(value) { sp.edit().putString("realIp", value).apply() }
    var proxy: String?
        get() = sp.getString("proxy", null)?.takeIf(String::isNotBlank)
        @Synchronized set(value) { sp.edit().putString("proxy", value).apply() }
    val userId: Long get() = sp.getLong("user_id", 0L)
    val nickname: String get() = sp.getString("user_nickname", "").orEmpty()
    val isLogin: Boolean get() = snapshot().loggedIn

    @Synchronized
    internal fun snapshot() = NcmSessionSnapshot(generation, cookies, os, realIp, proxy)

    @Synchronized
    internal fun accountIdentity(): Pair<Long, Long>? =
        if (userId > 0 && snapshot().loggedIn) userId to generation else null

    @Synchronized
    internal fun forAccount(user: Long, epoch: Long): NcmSessionSnapshot? =
        snapshot().takeIf { userId == user && generation == epoch && it.loggedIn }

    @Synchronized
    internal fun refresh(previous: NcmSessionSnapshot): NcmSessionSnapshot {
        check(previous.generation == generation) { "NetEase session changed" }
        return snapshot()
    }

    @Synchronized
    internal fun merge(expected: NcmSessionSnapshot, raw: List<String>) {
        if (expected.generation != generation || raw.isEmpty()) return
        val values = cookies
        for (header in raw) {
            val (key, value) = cookiePair(header.substringBefore(';').trim()) ?: continue
            if (value.isBlank() || header.substringAfter(';', "").split(';').any {
                    it.trim().equals("Max-Age=0", ignoreCase = true)
                }) values.remove(key) else values[key] = value
        }
        sp.edit().putString("cookies", NcmJson.toJsonString(values)).apply()
    }

    @Synchronized
    internal fun saveProfile(expected: NcmSessionSnapshot, userId: Long, name: String) {
        check(expected.generation == generation) { "NetEase session changed" }
        sp.edit().putLong("user_id", userId).putString("user_nickname", name).apply()
    }

    @Synchronized
    internal fun beginLogin(): NcmSessionSnapshot {
        logout()
        return snapshot()
    }

    @Synchronized
    internal fun logout(): NcmSessionSnapshot {
        val previous = snapshot()
        generation++
        sp.edit().remove("cookies").remove("user_id").remove("user_nickname").apply()
        return previous
    }

    private fun cookiePair(value: String): Pair<String, String>? {
        val index = value.indexOf('=')
        return if (index > 0) value.substring(0, index) to value.substring(index + 1) else null
    }
}
