@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package net.moriafly.ncm

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.random.Random

/**
 * NCM 网络请求引擎
 */
object NcmRequest {

    enum class OS(val ua: String, val appver: String) {
        PC(
            ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0",
            appver = "3.1.17.204416",
        ),
        ANDROID(
            ua = "NeteaseMusic/9.1.65.240927161425(9001065);Dalvik/2.1.0 (Linux; U; Android 14; 23013RK75C Build/UKQ1.230804.001)",
            appver = "8.20.20.231215173437",
        ),
        IOS(
            ua = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)",
            appver = "9.0.90",
        ),
    }

    private val deviceId: String by lazy { randomHex(32) }
    private val stableNuid: String by lazy { randomHex(32) }
    private val stableWnmcid: String by lazy { "${randomAlpha(6)}.${System.currentTimeMillis()}.01.0" }

    private fun osMeta(os: OS): Triple<String, String, String> = when (os) {
        OS.PC -> Triple("Microsoft-Windows-10-Professional-build-19045-64bit", os.appver, "netease")
        OS.ANDROID -> Triple("14", os.appver, "xiaomi")
        OS.IOS -> Triple("16.2", os.appver, "distribution")
    }

    private const val HEX = "0123456789abcdef"
    private const val ALPHA = "abcdefghijklmnopqrstuvwxyz"

    private fun randomHex(len: Int): String {
        val sb = StringBuilder(len)
        repeat(len) { sb.append(HEX[Random.nextInt(HEX.length)]) }
        return sb.toString()
    }

    private fun randomAlpha(len: Int): String {
        val sb = StringBuilder(len)
        repeat(len) { sb.append(ALPHA[Random.nextInt(ALPHA.length)]) }
        return sb.toString()
    }

    private val anonymousRegistration = AnonymousRegistration()

    private fun cloudmusicDllEncodeId(id: String): String {
        val key = "3go8&$8*3*3h0k(2)2"
        val xored = StringBuilder(id.length)
        for (i in id.indices) {
            xored.append((id[i].code xor key[i % key.length].code).toChar())
        }
        val digest = MessageDigest.getInstance("MD5")
            .digest(xored.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private suspend fun ensureAnonymousToken(session: NcmSession, expected: NcmSessionSnapshot) {
        anonymousRegistration.ensure(
            expected.generation,
            ready = {
                val current = session.refresh(expected)
                current.loggedIn || !current.cookies["MUSIC_A"].isNullOrBlank()
            }
        ) {
            val current = session.refresh(expected)
            val devId = randomHex(52).uppercase()
            val username = Base64.encodeToString(
                "$devId ${cloudmusicDllEncodeId(devId)}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
            )
            val body = NcmCrypto.weapi(mapOf("username" to username)).toFormBody().toByteArray(Charsets.UTF_8)
            val target = buildWeapiUrl("/api/register/anonimous")
            val headers = buildHeaders(
                host = URL(target).host, referer = WY_YX_BASE_URL + "/", osEnum = OS.PC,
                session = current, realIp = current.realIp,
                contentType = "application/x-www-form-urlencoded", accept = "*/*"
            )
            val raw = withTimeoutOrNull(1_500L) { http("POST", target, headers, body, current.proxy) }
                ?: return@ensure false
            val token = raw.setCookies.firstOrNull { it.startsWith("MUSIC_A=") }
                ?.takeIf { it.substringAfter('=').substringBefore(';').isNotBlank() }
            if (raw.code !in 200..299 || token == null) false
            else {
                session.merge(expected, listOf(token))
                true
            }
        }
    }

    private val CHINA_IP_RANGES = listOf(
        "36.56.", "36.57.", "39.128.", "39.129.", "42.48.", "42.49.",
        "49.64.", "49.65.", "58.17.", "58.18.", "60.13.", "60.14.",
        "114.80.", "114.81.", "115.56.", "115.57.", "116.25.", "116.26.",
        "120.48.", "120.49.", "121.60.", "121.61.", "123.56.", "123.57.",
    )

    private val randomChinaIp: String? by lazy {
        CHINA_IP_RANGES.randomOrNull()?.let { seg ->
            "$seg${Random.nextInt(0, 256)}.${Random.nextInt(0, 256)}"
        }
    }

    private fun deviceFingerprintCookies(os: OS, session: NcmSessionSnapshot?, includeNmtid: Boolean): String {
        val now = System.currentTimeMillis()
        val (osver, appver, channel) = osMeta(os)
        val existing = session?.cookies ?: emptyMap()
        val sb = StringBuilder()
        fun appendIfMissing(key: String, value: String) {
            if (key in existing) return
            if (sb.isNotEmpty()) sb.append("; ")
            sb.append("$key=$value")
        }
        appendIfMissing("__remember_me", "true")
        appendIfMissing("ntes_kaola_ad", "1")
        appendIfMissing("_ntes_nuid", stableNuid)
        appendIfMissing("_ntes_nnid", "$stableNuid,$now")
        appendIfMissing("WNMCID", stableWnmcid)
        appendIfMissing("WEVNSM", "1.0.0")
        if (includeNmtid) appendIfMissing("NMTID", randomHex(16))
        appendIfMissing("osver", osver)
        appendIfMissing("deviceId", deviceId)
        appendIfMissing("os", os.name.lowercase())
        appendIfMissing("channel", channel)
        appendIfMissing("appver", appver)
        return sb.toString()
    }

    private const val MAX_RAW_BODY = 8 * 1024 * 1024
    private const val MAX_DECODED_BODY = 16 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .callTimeout(35L, TimeUnit.SECONDS)
        .build()

    const val WY_YX_BASE_URL = "https://music.163.com"
    const val WY_INTERFACE_EAPI_BASE_URL = "https://interface3.music.163.com"
    const val WY_LINUXAPI_BASE_URL = "https://music.163.com"

    suspend fun weapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null
    ): Result<NcmResponse> = weapiForSession(path, params, realIp, url)

    internal suspend fun weapiForSession(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null,
        expected: NcmSessionSnapshot? = null,
        logout: Boolean = false
    ): Result<NcmResponse> = request(path, url ?: buildWeapiUrl(path), expected, logout) { state ->
        val os = state?.os.orEmpty().ifBlank { "pc" }
        val enriched = LinkedHashMap(params)
        state?.cookies?.get("__csrf")?.takeIf(String::isNotBlank)?.let { enriched["csrf_token"] = it }
        RequestParts(
            NcmCrypto.weapi(enriched).toFormBody(), when (os.lowercase()) {
                "android" -> OS.ANDROID
                "ios" -> OS.IOS
                else -> OS.PC
            }, realIp, WY_YX_BASE_URL + "/", "os=$os"
        )
    }

    suspend fun eapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null
    ): Result<NcmResponse> = request(path, url ?: buildEapiUrl(path), isEapi = true) {
        RequestParts(NcmCrypto.eapi(path, params).toFormBody(), OS.ANDROID, realIp)
    }

    suspend fun linuxapi(
        path: String,
        params: Map<String, Any?>,
        url: String? = null
    ): Result<NcmResponse> = request(path, url ?: (WY_LINUXAPI_BASE_URL + path)) {
        RequestParts(NcmCrypto.linuxapi(params).toFormBody(), OS.PC)
    }

    private data class RequestParts(
        val encrypted: String, val os: OS, val realIp: String? = null,
        val referer: String? = null, val extraCookie: String? = null
    )

    private suspend fun request(
        path: String, target: String, expected: NcmSessionSnapshot? = null, logout: Boolean = false,
        isEapi: Boolean = false, parts: (NcmSessionSnapshot?) -> RequestParts
    ): Result<NcmResponse> = withContext(Dispatchers.IO) {
        try {
            val origin = URL(target)
            require(NcmEndpoint.acceptsSession(origin)) { "Untrusted NetEase endpoint" }
            val session = NcmSession.INSTANCE
            val initial = expected ?: session?.snapshot()
            val state = if (!logout && initial != null && session != null) {
                session.refresh(initial)
                ensureAnonymousToken(session, initial)
                session.refresh(initial)
            } else initial
            val data = parts(state)
            val payload = data.encrypted.toByteArray(Charsets.UTF_8)
            val headers = buildHeaders(
                host = origin.host, referer = data.referer, osEnum = data.os, session = state,
                extraCookie = data.extraCookie, realIp = data.realIp ?: state?.realIp,
                contentType = "application/x-www-form-urlencoded", accept = "*/*"
            )
            val raw = http("POST", target, headers, payload, state?.proxy)
            val response = handleRawResponse(raw, origin, path, if (logout) null else session, state, isEapi, headers, payload)
            if (!logout && initial != null) session?.refresh(initial)
            Result.success(response)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    internal data class Raw(
        val code: Int,
        val bodyBytes: ByteArray,
        val location: String?,
        val setCookies: List<String>,
    )

    private suspend fun handleRawResponse(
        raw: Raw, from: URL, path: String, session: NcmSession?, state: NcmSessionSnapshot?,
        isEapi: Boolean, requestHeaders: Map<String, String>, payload: ByteArray
    ): NcmResponse {
        var response = raw
        if (response.code in setOf(301, 302, 303, 307, 308)) {
            val location = checkNotNull(response.location) { "NetEase redirect without Location" }
            if (path.contains("/song/url") || path.contains("player/url")) {
                val media = URL(from, location)
                require(media.protocol == "https" && media.userInfo == null) { "Untrusted media URL" }
                return NcmResponse(200, mapOf("data" to listOf(mapOf("url" to media.toExternalForm()))), null)
            }
            val target = NcmEndpoint.redirect(from, location)
            if (state != null) session?.merge(state, response.setCookies)
            val redirectState = state?.let { session?.refresh(it) ?: it }
            val preservePost = response.code == 307 || response.code == 308
            val headers = requestHeaders.toMutableMap().apply {
                if (!preservePost) remove("Content-Type")
                if (redirectState != null && session != null) {
                    val latest = redirectState.cookies.entries.joinToString("; ") { (key, value) -> "$key=$value" }
                    if (latest.isNotEmpty()) put("Cookie", latest) else remove("Cookie")
                }
            }
            response = http(if (preservePost) "POST" else "GET", target.toExternalForm(), headers,
                payload.takeIf { preservePost }, redirectState?.proxy)
        }
        val parsed = parseResponse(response.code, response.bodyBytes, isEapi)
        if (state != null && (parsed.isSuccess || parsed.code == 803)) session?.merge(state, response.setCookies)
        return parsed
    }

    internal fun parseResponse(httpCode: Int, bodyBytes: ByteArray, isEapi: Boolean = false): NcmResponse {
        if (httpCode !in 200..299) throw NcmHttpException(httpCode)
        if (httpCode == 204) return NcmResponse(200, emptyMap(), null)
        val body = decompressIfNeeded(bodyBytes).toString(Charsets.UTF_8)
        val parsed = if (isEapi) runCatching { NcmCrypto.eapiResDecrypt(body) }.getOrNull()
            ?: NcmJson.parseAny(body) else NcmJson.parseAny(body)
        @Suppress("UNCHECKED_CAST")
        val map = parsed as? Map<String, Any?> ?: error("NetEase response is not a JSON object")
        val code = if ("code" in map) map["code"].toString().toIntOrNull()
            ?: error("Invalid NetEase business code") else 200
        return NcmResponse(code, map, (map["message"] ?: map["msg"])?.toString())
    }

    private fun decompressIfNeeded(data: ByteArray): ByteArray {
        if (data.size < 2) return data
        if (data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
            return GZIPInputStream(data.inputStream()).use { it.readBounded(MAX_DECODED_BODY) }
        }
        if (data[0] == 0x78.toByte() && (((data[0].toInt() and 255) shl 8) + (data[1].toInt() and 255)) % 31 == 0) {
            return InflaterInputStream(data.inputStream()).use { it.readBounded(MAX_DECODED_BODY) }
        }
        return data
    }

    private fun buildWeapiUrl(path: String): String = when {
        path.startsWith("/api/") -> WY_YX_BASE_URL + "/weapi/" + path.removePrefix("/api/")
        path.startsWith("/weapi/") || path.startsWith("/eapi/") -> WY_YX_BASE_URL + path
        else -> WY_YX_BASE_URL + path
    }

    private fun buildEapiUrl(path: String): String = when {
        path.startsWith("/api/") -> WY_INTERFACE_EAPI_BASE_URL + "/eapi/" + path.removePrefix("/api/")
        path.startsWith("/eapi/") -> WY_INTERFACE_EAPI_BASE_URL + path
        else -> WY_INTERFACE_EAPI_BASE_URL + path
    }

    private fun buildHeaders(
        host: String,
        referer: String?,
        osEnum: OS,
        session: NcmSessionSnapshot?,
        extraCookie: String? = null,
        realIp: String? = null,
        contentType: String? = null,
        accept: String? = null,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["Host"] = host
        headers["User-Agent"] = osEnum.ua
        if (accept != null) headers["Accept"] = accept
        if (contentType != null) headers["Content-Type"] = contentType
        headers["Accept-Encoding"] = "gzip"
        headers["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8"
        if (referer != null) headers["Referer"] = referer
        headers["Connection"] = "keep-alive"

        val effRealIp = realIp ?: randomChinaIp
        if (effRealIp != null) {
            headers["X-Real-IP"] = effRealIp
            headers["X-Forwarded-For"] = effRealIp
        }

        val cookieSb = StringBuilder()
        session?.cookies?.entries?.joinToString("; ") { (key, value) -> "$key=$value" }?.let(cookieSb::append)
        if (extraCookie != null) {
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append(extraCookie)
        }
        if (osEnum == OS.ANDROID) {
            val now = System.currentTimeMillis()
            val (osver, appver, channel) = osMeta(OS.ANDROID)
            val csrf = session?.cookies?.get("__csrf") ?: ""
            val headerCookie = buildString {
                append("osver=$osver; deviceId=$deviceId; os=android; appver=$appver; ")
                append("versioncode=${appver.replace(".", "")}; mobilename=; ")
                append("buildver=${now.toString().substring(0, 10)}; resolution=1920x1080; ")
                append("__csrf=$csrf; channel=$channel; ")
                append("requestId=${now}_${Random.nextInt(1000).toString().padStart(4, '0')}")
            }
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append(headerCookie)
        } else {
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append(deviceFingerprintCookies(osEnum, session, includeNmtid = true))
        }
        if (cookieSb.isNotBlank()) headers["Cookie"] = cookieSb.toString()

        return headers
    }

    private suspend fun http(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        proxy: String?,
    ): Raw {
        require(NcmEndpoint.acceptsSession(URL(url))) { "Untrusted NetEase endpoint" }
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            method(method, body?.let { RequestBody.create(MediaType.parse(headers["Content-Type"].orEmpty()), it) })
        }.build()
        val transport = if (proxy.isNullOrBlank()) client else client.newBuilder().proxy(parseProxy(proxy)).build()
        return transport.newCall(request).readResponse {
            Raw(it.code(), if (it.isSuccessful) it.body()?.readBounded(MAX_RAW_BODY) ?: ByteArray(0) else ByteArray(0), it.header("Location"), it.headers("Set-Cookie"))
        }
    }

    private fun parseProxy(s: String): Proxy {
        val noScheme = s.removePrefix("http://").removePrefix("https://").removeSuffix("/")
        val parts = noScheme.split(':', limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        return Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress.createUnresolved(host, port))
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

data class NcmResponse(
    val code: Int,
    val body: Map<String, Any?>,
    val message: String?,
) {
    val isSuccess: Boolean get() = code == 200

    fun checkedBody(vararg acceptedCodes: Int): Map<String, Any?> {
        if (if (acceptedCodes.isEmpty()) isSuccess else code in acceptedCodes) return body
        throw NcmBusinessException(code)
    }

    inline fun <T> map(block: (Map<String, Any?>) -> T): Result<T> =
        if (isSuccess) runCatching { block(body) }
        else Result.failure(IllegalStateException("NCM code=$code msg=$message"))
}

class NcmHttpException(val status: Int) : java.io.IOException("NetEase HTTP $status")
class NcmBusinessException(val code: Int) : IllegalStateException("NetEase business code $code")

internal class AnonymousRegistration(private val nanoTime: () -> Long = System::nanoTime) {
    private val lock = Mutex()
    private var generation = -1L
    private var retryAt: Long? = null

    suspend fun ensure(currentGeneration: Long, ready: () -> Boolean, register: suspend () -> Boolean) {
        lock.withLock {
            if (generation != currentGeneration) {
                generation = currentGeneration
                retryAt = null
            }
            if (ready() || retryAt?.let { nanoTime() - it < 0 } == true) return
            val success = try {
                register()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            retryAt = if (success) null else nanoTime() + 5_000_000_000L
        }
    }
}
