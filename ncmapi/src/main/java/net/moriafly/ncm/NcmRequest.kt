@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package net.moriafly.ncm

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.HttpsURLConnection
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

    @Volatile
    private var anonymousReady = false
    @Volatile
    private var anonymousRegistering = false
    private val anonymousLock = Any()

    @Volatile
    private var anonymousToken: String? = null

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

    private suspend fun ensureAnonymousToken(sess: NcmSession?) {
        if (anonymousReady) return
        if (sess?.isLogin == true) { anonymousReady = true; return }
        if (sess?.cookies?.containsKey("MUSIC_A") == true) { anonymousReady = true; return }
        synchronized(anonymousLock) {
            if (anonymousReady || anonymousRegistering) return
            anonymousRegistering = true
        }
        try {
            val devId = randomHex(52).uppercase()
            val username = Base64.encodeToString(
                "$devId ${cloudmusicDllEncodeId(devId)}".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
            val encrypted = NcmCrypto.weapi(mapOf("username" to username))
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val target = buildWeapiUrl("/api/register/anonimous")
            val headers = buildHeaders(
                host = URL(target).host,
                referer = WY_YX_BASE_URL + "/",
                osEnum = OS.PC,
                session = sess,
                realIp = sess?.realIp,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )
            val raw = http("POST", target, headers, body, sess?.proxy)
            raw.setCookies.forEach { rawCookie ->
                val head = rawCookie.split(';').firstOrNull()?.trim() ?: return@forEach
                val idx = head.indexOf('=')
                if (idx > 0 && head.substring(0, idx) == "MUSIC_A") {
                    head.substring(idx + 1).takeIf { it.isNotBlank() }?.let { anonymousToken = it }
                }
            }
            if (raw.setCookies.isNotEmpty()) sess?.merge(raw.setCookies)
        } catch (_: Throwable) {
        } finally {
            anonymousRegistering = false
            anonymousReady = true
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

    private fun deviceFingerprintCookies(os: OS, session: NcmSession?, includeNmtid: Boolean): String {
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

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    const val WY_YX_BASE_URL = "https://music.163.com"
    const val WY_INTERFACE_EAPI_BASE_URL = "https://interface3.music.163.com"
    const val WY_LINUXAPI_BASE_URL = "https://music.163.com"

    suspend fun weapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null,
    ): Result<NcmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = NcmSession.INSTANCE
            ensureAnonymousToken(sess)
            val osValue = sess?.os?.takeIf { it.isNotBlank() } ?: "pc"
            val osEnum = when (osValue.lowercase()) {
                "android" -> OS.ANDROID
                "ios" -> OS.IOS
                else -> OS.PC
            }

            val csrf = sess?.cookies?.get("__csrf").orEmpty()
            val enriched = LinkedHashMap(params)
            if (csrf.isNotBlank()) enriched["csrf_token"] = csrf

            val encrypted = NcmCrypto.weapi(enriched)
            val target = url ?: buildWeapiUrl(path)
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val headers = buildHeaders(
                host = URL(target).host,
                referer = WY_YX_BASE_URL + "/",
                osEnum = osEnum,
                session = sess,
                extraCookie = "os=$osValue",
                realIp = realIp ?: sess?.realIp,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )

            val raw = http("POST", target, headers, body, sess?.proxy)
            handleRawResponse(raw, path, sess, isEapi = false)
        }
    }

    suspend fun eapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null,
    ): Result<NcmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = NcmSession.INSTANCE
            ensureAnonymousToken(sess)
            val osEnum = OS.ANDROID
            val fullUrl = url ?: buildEapiUrl(path)
            val encrypted = NcmCrypto.eapi(path, params)
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val headers = buildHeaders(
                host = URL(fullUrl).host,
                referer = null,
                osEnum = osEnum,
                session = sess,
                realIp = realIp ?: sess?.realIp,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )
            val raw = http("POST", fullUrl, headers, body, sess?.proxy)
            handleRawResponse(raw, path, sess, isEapi = true)
        }
    }

    suspend fun linuxapi(
        path: String,
        params: Map<String, Any?>,
        url: String? = null,
    ): Result<NcmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = NcmSession.INSTANCE
            ensureAnonymousToken(sess)
            val target = url ?: (WY_LINUXAPI_BASE_URL + path)
            val encrypted = NcmCrypto.linuxapi(params)
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val headers = buildHeaders(
                host = URL(target).host,
                referer = null,
                osEnum = OS.PC,
                session = sess,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )
            val raw = http("POST", target, headers, body, sess?.proxy)
            handleRawResponse(raw, path, sess, isEapi = false)
        }
    }

    internal data class Raw(
        val code: Int,
        val bodyBytes: ByteArray,
        val location: String?,
        val setCookies: List<String>,
    )

    private fun handleRawResponse(
        raw: Raw,
        path: String,
        sess: NcmSession?,
        isEapi: Boolean,
    ): NcmResponse {
        if (raw.setCookies.isNotEmpty()) sess?.merge(raw.setCookies)

        if (raw.code in 300..399) {
            val loc = raw.location
                ?: return NcmResponse(raw.code, emptyMap(), "3xx No Location")

            if (path.contains("/song/url") || path.contains("player/url")) {
                val fake = mapOf(
                    "code" to raw.code,
                    "data" to listOf(mapOf("url" to loc))
                )
                return NcmResponse(raw.code, fake, null)
            }

            val followHeaders = buildHeaders(
                host = URL(loc).host,
                referer = null,
                osEnum = OS.PC,
                session = sess,
                accept = "*/*",
            )
            val raw2 = http("GET", loc, followHeaders, null, sess?.proxy)
            if (raw2.setCookies.isNotEmpty()) sess?.merge(raw2.setCookies)
            return parseBody(raw2.code, raw2.bodyBytes, path, isEapi = false)
        }

        return parseBody(raw.code, raw.bodyBytes, path, isEapi)
    }

    private fun parseBody(code: Int, bodyBytes: ByteArray, path: String, isEapi: Boolean): NcmResponse {
        val body = try {
            String(decompressGzipIfNeeded(bodyBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            String(bodyBytes, Charsets.ISO_8859_1)
        }
        val parsed: Any? = when {
            body.isBlank() -> null
            isEapi -> runCatching { NcmCrypto.eapiResDecrypt(body) }.getOrNull()
                ?: runCatching { NcmJson.parseAny(body) }.getOrNull()
            else -> runCatching { NcmJson.parseAny(body) }.getOrNull()
        }
        val map = parsed as? Map<String, Any?> ?: emptyMap()
        val respCode = map["code"] as? Int ?: code
        val msg = when {
            map.contains("message") -> map["message"]?.toString()
            map.contains("msg") -> map["msg"]?.toString()
            else -> null
        }
        return NcmResponse(respCode, map, msg)
    }

    private fun decompressGzipIfNeeded(data: ByteArray): ByteArray {
        if (data.size < 2) return data
        if (data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
            return GZIPInputStream(data.inputStream()).use { it.readBytesCompat() }
        }
        if (data[0] == 0x78.toByte()) {
            return try {
                InflaterInputStream(data.inputStream()).use { it.readBytesCompat() }
            } catch (_: Throwable) {
                data
            }
        }
        return data
    }

    private fun InputStream.readBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var n: Int
        while (true) {
            n = this.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
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
        session: NcmSession?,
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
        session?.toCookieHeader()?.takeIf { it.isNotBlank() }?.let {
            cookieSb.append(it)
        }
        val hasMusicU = session?.cookies?.containsKey("MUSIC_U") == true
        val musicA = anonymousToken ?: session?.cookies?.get("MUSIC_A")
        if (!hasMusicU && musicA != null) {
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append("MUSIC_A=$musicA")
        }
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
                val cu = session?.cookies?.get("MUSIC_U")
                if (!cu.isNullOrBlank()) append("; MUSIC_U=$cu")
                val ca = anonymousToken ?: session?.cookies?.get("MUSIC_A")
                if (!ca.isNullOrBlank()) append("; MUSIC_A=$ca")
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

    private fun http(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        proxy: String?,
    ): Raw {
        val u = URL(url)
        val conn = if (proxy.isNullOrBlank()) u.openConnection() else u.openConnection(parseProxy(proxy))

        conn as HttpURLConnection
        if (conn is HttpsURLConnection) {
            // Keep default system SSL/TLS
        }
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        conn.doInput = true
        conn.doOutput = body != null
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        if (body != null) {
            conn.outputStream.use { it.write(body) }
        }

        return try {
            val code = conn.responseCode
            val setCookies = extractSetCookies(conn)
            val loc = conn.getHeaderField("Location")
            val stream = runCatching {
                if (code in 200..299) conn.inputStream else conn.errorStream
            }.getOrNull()
            val bytes = stream?.use { it.readBytesCompat() } ?: ByteArray(0)
            Raw(code, bytes, loc, setCookies)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun extractSetCookies(conn: HttpURLConnection): List<String> {
        val out = ArrayList<String>(2)
        var i = 0
        while (true) {
            val k = conn.getHeaderFieldKey(i) ?: break
            if (k.equals("Set-Cookie", ignoreCase = true)) {
                conn.getHeaderField(i)?.let(out::add)
            }
            i++
        }
        return out
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

    inline fun <T> map(block: (Map<String, Any?>) -> T): Result<T> =
        if (isSuccess) runCatching { block(body) }
        else Result.failure(IllegalStateException("NCM code=$code msg=$message"))
}
