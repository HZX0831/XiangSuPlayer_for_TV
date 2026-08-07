@file:Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection", "TooManyFunctions")

package net.moriafly.ncm

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 网易云音乐 SDK 门面
 */
object NcmApi {

    fun install(
        appContext: Context,
        os: String = "pc",
        realIp: String? = null,
        proxy: String? = null,
    ) {
        val ctx = appContext.applicationContext
        val session = NcmSession.install(ctx)
        session.os = os
        session.realIp = realIp
        session.proxy = proxy
        if (installed) return
        installed = true
    }

    fun install(app: Application) = install(app.applicationContext)

    @Volatile
    private var installed: Boolean = false

    private fun ensureInstalled() {
        if (!installed && NcmSession.INSTANCE == null) {
            error("NcmApi not installed! Call NcmApi.install(this) first.")
        }
    }

    val isLogin: Boolean get() = NcmSession.INSTANCE?.isLogin == true
    val userId: Long get() = NcmSession.INSTANCE?.userId ?: 0L
    val nickname: String get() = NcmSession.INSTANCE?.nickname ?: ""

    fun setOs(os: String) { NcmSession.INSTANCE?.os = os }
    fun setRealIp(ip: String?) { NcmSession.INSTANCE?.realIp = ip }
    fun setProxy(proxy: String?) { NcmSession.INSTANCE?.proxy = proxy }

    suspend fun fetchAndSaveAccountInfo(): Result<Pair<Long, String>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInstalled()
            val res = userAccount().getOrThrow()
            val profile = res.ncmObj("profile")
            val account = res.ncmObj("account")

            var uid = profile.ncmLong("userId")
            if (uid <= 0L) uid = profile.ncmLong("id")
            if (uid <= 0L) uid = account.ncmLong("id")

            var nick = profile.ncmString("nickname")
            if (nick.isBlank()) nick = profile.ncmString("name")
            if (nick.isBlank()) nick = account.ncmString("userName")
            if (nick.isBlank()) nick = "网易云用户"

            if (uid > 0L) NcmSession.INSTANCE?.userId = uid
            if (nick.isNotBlank()) NcmSession.INSTANCE?.nickname = nick

            uid to nick
        }
    }

    suspend fun searchDefault() = runSafely("searchDefault") {
        ensureInstalled()
        NcmModules.searchDefault()
    }

    suspend fun search(
        keyword: String,
        type: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
    ) = runSafely("search") {
        ensureInstalled()
        NcmModules.search(keyword, type, limit, offset)
    }

    suspend fun songUrlV1(
        ids: List<String>,
        level: String = "standard",
        encodeType: String = "flac",
    ) = runSafely("songUrlV1") {
        ensureInstalled()
        NcmModules.songUrlV1(ids, level, encodeType)
    }

    suspend fun songUrl(id: String, br: Int = 320_000) = songUrlV1(
        ids = listOf(id),
        level = when (br) {
            in 0..128_000 -> "standard"
            in 128_001..192_000 -> "higher"
            in 192_001..320_000 -> "exhigh"
            in 320_001..1_411_000 -> "lossless"
            else -> "hires"
        },
        encodeType = if (br > 1_500_000) "flac" else "mp3",
    )

    suspend fun checkMusicPlayable(id: String, br: Int = 320_000) = runSafely("checkMusic") {
        ensureInstalled()
        NcmModules.checkMusicPlayable(id, br)
    }

    suspend fun songDetail(ids: List<String>) = runSafely("songDetail") {
        ensureInstalled()
        NcmModules.songDetail(ids)
    }

    suspend fun songLyric(id: String) = runSafely("songLyric") {
        ensureInstalled()
        NcmModules.songLyric(id)
    }

    suspend fun sentSmsCaptcha(phone: String, ctcode: String = "86") = runSafely("sentSmsCaptcha") {
        ensureInstalled()
        NcmModules.sentSmsCaptcha(phone, ctcode)
    }

    suspend fun captchaVerify(phone: String, captcha: String, ctcode: String = "86") =
        runSafely("captchaVerify") {
            ensureInstalled()
            NcmModules.captchaVerify(phone, captcha, ctcode)
        }

    suspend fun loginCellphone(
        phone: String,
        captcha: String? = null,
        password: String? = null,
        ctcode: String = "86",
        countrycode: String? = null,
    ) = runSafely("loginCellphone") {
        ensureInstalled()
        NcmModules.loginCellphone(phone, captcha, password, ctcode, countrycode)
    }

    suspend fun loginRefresh() = runSafely("loginRefresh") {
        ensureInstalled()
        NcmModules.loginRefresh()
    }

    suspend fun logout() = runSafely("logout") {
        ensureInstalled()
        NcmModules.logout()
    }

    data class QrLoginResult(
        val key: String,
        val qrPngBase64: String,
    )

    suspend fun qrLoginPrepare(): Result<QrLoginResult> = runSafely("qrLoginPrepare") {
        ensureInstalled()
        val k = NcmModules.qrLoginKey().getOrThrow()
        val img = NcmModules.qrLoginImage(k.key).getOrThrow()
        Result.success(QrLoginResult(k.key, img))
    }

    suspend fun qrLoginAwait(
        key: String,
        onStatus: suspend (code: Int, raw: Map<String, Any?>) -> Unit = { _, _ -> },
    ) = runSafely("qrLoginAwait") {
        ensureInstalled()
        NcmModules.qrLoginAwait(key, onStatus)
    }

    suspend fun banner(type: Int = 1) = runSafely("banner") {
        ensureInstalled()
        NcmModules.banner(type)
    }

    suspend fun playlistDetail(id: String) = runSafely("playlistDetail") {
        ensureInstalled()
        NcmModules.playlistDetail(id)
    }

    suspend fun recommendSongs() = runSafely("recommendSongs") {
        ensureInstalled()
        NcmModules.recommendSongs()
    }

    suspend fun recommendPlaylists() = runSafely("recommendPlaylists") {
        ensureInstalled()
        NcmModules.recommendPlaylists()
    }

    suspend fun personalizedPlaylists(limit: Int = 10) = runSafely("personalizedPlaylists") {
        ensureInstalled()
        NcmModules.personalizedPlaylists(limit)
    }

    suspend fun userAccount() = runSafely("userAccount") {
        ensureInstalled()
        NcmModules.userAccount()
    }

    suspend fun userPlaylist(uid: String, limit: Int = 30, offset: Int = 0) = runSafely("userPlaylist") {
        ensureInstalled()
        NcmModules.userPlaylist(uid, limit, offset)
    }

    private suspend inline fun <T> runSafely(
        tag: String,
        crossinline block: suspend () -> Result<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (t: Throwable) {
            Result.failure(t as? Exception ?: Exception(t.message ?: "unknown", t))
        }
    }
}
