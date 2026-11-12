package net.moriafly.ncm

import android.content.Context
import android.content.SharedPreferences
import java.net.HttpCookie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@ConsistentCopyVisibility
data class NcmAccountState internal constructor(
    val hasCredential: Boolean,
    val userId: Long = 0L,
    val nickname: String = "",
    internal val generation: Long = 0L
) {
    val isReady: Boolean get() = hasCredential && userId > 0L
}

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
    private val _accountState = MutableStateFlow(readAccountState())
    val accountState = _accountState.asStateFlow()

    private fun readAccountState(hasCredential: Boolean = !cookies["MUSIC_U"].isNullOrBlank()): NcmAccountState =
        if (hasCredential) NcmAccountState(true, userId, nickname, generation)
        else NcmAccountState(false, generation = generation)

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
        val wasLoggedIn = !values["MUSIC_U"].isNullOrBlank()
        for (header in raw) {
            val (key, value) = cookiePair(header.substringBefore(';').trim()) ?: continue
            if (value.isBlank() || header.substringAfter(';', "").split(';').any {
                    it.trim().equals("Max-Age=0", ignoreCase = true)
                }) values.remove(key) else values[key] = value
        }
        val hasCredential = !values["MUSIC_U"].isNullOrBlank()
        if (wasLoggedIn && !hasCredential) {
            logout()
        } else {
            sp.edit().putString("cookies", NcmJson.toJsonString(values)).apply()
            if (wasLoggedIn != hasCredential) _accountState.value = readAccountState(hasCredential)
        }
    }

    @Synchronized
    internal fun revokeOnUnauthorized(expected: NcmSessionSnapshot, headers: List<String>) {
        if (expected.generation != generation || !expected.loggedIn) return
        if (headers.any { header ->
                runCatching { HttpCookie.parse(header) }.getOrDefault(emptyList()).any {
                    it.name == "MUSIC_U" && (it.value.isNullOrBlank() || it.hasExpired())
                }
            }) logout()
    }

    @Synchronized
    internal fun saveProfile(expected: NcmSessionSnapshot, userId: Long, name: String) {
        check(expected.generation == generation && !cookies["MUSIC_U"].isNullOrBlank()) { "NetEase session changed" }
        sp.edit().putLong("user_id", userId).putString("user_nickname", name).apply()
        _accountState.value = readAccountState(hasCredential = true)
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
        _accountState.value = readAccountState(hasCredential = false)
        return previous
    }

    private fun cookiePair(value: String): Pair<String, String>? {
        val index = value.indexOf('=')
        return if (index > 0) value.substring(0, index) to value.substring(index + 1) else null
    }
}
