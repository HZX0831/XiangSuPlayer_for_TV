package com.cutedino.xiangsuplayer.core.library

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import net.moriafly.ncm.readResponse
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.TimeUnit

internal object WebDavClient {
    val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val playbackTransport = http.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
    fun playbackClient(seekable: Boolean?) = if (seekable == false) playbackTransport else rangedPlayback
    private val rangedPlayback = playbackTransport.newBuilder().addInterceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val requested = request.header("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull()
        val valid = if (response.code == 206) {
            val range = Regex("""bytes (\d+)-(\d+)/(\d+)""").matchEntire(response.header("Content-Range").orEmpty())
            val start = range?.groupValues?.get(1)?.toLongOrNull()
            val end = range?.groupValues?.get(2)?.toLongOrNull()
            val length = range?.groupValues?.get(3)?.toLongOrNull()
            start != null && end != null && length != null && start == (requested ?: 0L) && end in start until length
        } else response.code != 200 || requested == null || requested == 0L
        if (!valid) {
            response.close()
            throw java.io.IOException("Invalid WebDAV range response")
        }
        response
    }.build()
    private val contentRange = Regex("""bytes 0-0/([1-9]\d*)""")
    private val successStatus = Regex("""HTTP/\S+ 2\d\d(?: .*)?""")
    private const val MAX_LISTING_BYTES = 16_777_216L
    private const val MAX_ITEMS = 16_000
    private val properties = """
        <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:displayname/></d:prop></d:propfind>
    """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

    fun root(value: String): HttpUrl? {
        val url = value.trim().toHttpUrlOrNull() ?: return null
        if (!safe(url) || url.query != null) return null
        return if (url.encodedPath.endsWith('/')) url else url.newBuilder().addPathSegment("").build()
    }

    fun child(root: HttpUrl, base: HttpUrl, href: String): HttpUrl? {
        if (href.isBlank() || href.any { it.isISOControl() || it == '\\' }) return null
        val url = base.resolve(href) ?: return null
        if (!safe(url) || url.host != root.host || url.port != root.port || url.query != null) return null
        val parent = root.pathSegments.dropLast(1)
        if (url.pathSegments.take(parent.size) != parent) return null
        return url
    }

    private fun safe(url: HttpUrl) =
        url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
            url.pathSegments.none { it == "." || it == ".." || it.any { char -> char == '/' || char == '\\' || char.isISOControl() } }

    suspend fun list(root: HttpUrl, directory: HttpUrl, authorization: String?): DavListing {
        require(child(root, root, directory.toString()) == directory) { "WebDAV directory outside configured root" }
        val request = request(directory, authorization).header("Depth", "1").method("PROPFIND", properties).build()
        return http.newCall(request).readResponse { response ->
            check(response.code == 207) { if (response.code in 401..403) "WebDAV 认证失败" else "WebDAV 目录请求失败" }
            parse(checkNotNull(response.body).byteStream(), root, directory)
        }
    }

    suspend fun canSeek(url: HttpUrl, authorization: String?): Boolean =
        http.newCall(request(url, authorization).header("Range", "bytes=0-0").get().build()).readResponse { response ->
            rangeSupport(response.code, response.header("Content-Range"))
        }

    internal fun rangeSupport(status: Int, header: String?): Boolean = when (status) {
        200, 400, 405, 416, 501 -> false
        206 -> validRange(status, header)
        401, 403 -> error("WebDAV 认证失败")
        else -> error("WebDAV 文件不可访问 (HTTP $status)")
    }

    fun validRange(status: Int, value: String?): Boolean =
        status == 206 && contentRange.matchEntire(value.orEmpty())?.groupValues?.get(1)?.toLongOrNull() != null

    private fun request(url: HttpUrl, authorization: String?) = Request.Builder().url(url)
        .header("Accept-Encoding", "identity")
        .apply { if (authorization != null) header("Authorization", authorization) }

    internal fun parse(input: InputStream, root: HttpUrl, directory: HttpUrl): DavListing {
        val limited = ListingInput(input, MAX_LISTING_BYTES)
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(limited, null)
        val result = mutableListOf<DavItem>()
        var responseDepth = -1
        var propertiesDepth = -1
        var href = ""
        var label = ""
        var collection = false
        var accepted: Pair<String, Boolean>? = null
        var propOk = false
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                val event = parser.eventType
                require(event != XmlPullParser.DOCDECL && event != XmlPullParser.ENTITY_REF) { "非法 WebDAV XML" }
                if (event == XmlPullParser.START_TAG && parser.namespace == "DAV:") {
                    when (parser.name) {
                        "response" -> { responseDepth = parser.depth; href = ""; accepted = null }
                        "href" -> if (responseDepth == parser.depth - 1) href = parser.nextText().trim()
                        "propstat" -> if (responseDepth == parser.depth - 1) {
                            propertiesDepth = parser.depth; label = ""; collection = false; propOk = false
                        }
                        "status" -> if (propertiesDepth == parser.depth - 1) propOk = successStatus.matches(parser.nextText().trim())
                        "displayname" -> if (propertiesDepth == parser.depth - 2) label = parser.nextText().take(240)
                        "collection" -> if (propertiesDepth == parser.depth - 3) collection = true
                    }
                } else if (event == XmlPullParser.END_TAG && parser.namespace == "DAV:") {
                    if (parser.name == "propstat" && parser.depth == propertiesDepth) {
                        if (propOk) accepted = label.ifBlank { accepted?.first.orEmpty() } to
                            (collection || accepted?.second == true)
                        propertiesDepth = -1
                    } else if (parser.name == "response" && parser.depth == responseDepth) {
                        val url = child(root, directory, href)
                        val props = accepted
                        if (url != null && props != null &&
                            url.pathSegments.filter(String::isNotEmpty).size == directory.pathSegments.filter(String::isNotEmpty).size + 1 &&
                            url.encodedPath.startsWith(directory.encodedPath)) {
                            val name = props.first.ifBlank { url.pathSegments.lastOrNull(String::isNotBlank).orEmpty() }
                            result.add(DavItem(url, name, props.second))
                            if (result.size >= MAX_ITEMS) return DavListing(result.distinctBy { it.url }, incomplete = true)
                        }
                        responseDepth = -1
                    }
                }
                parser.nextToken()
            }
        } catch (failure: Exception) {
            if (limited.exceeded) return DavListing(result.distinctBy { it.url }, incomplete = true)
            throw failure
        }
        return DavListing(result.distinctBy { it.url }, incomplete = false)
    }
}

internal data class DavItem(val url: HttpUrl, val name: String, val directory: Boolean)


internal data class DavListing(val items: List<DavItem>, val incomplete: Boolean)

private class ListingInput(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
    var exceeded = false
        private set

    override fun read(): Int {
        val result = super.read()
        if (result >= 0) consumed(1)
        return result
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, minOf(length.toLong(), remaining + 1).toInt())
        if (count > 0) consumed(count)
        return count
    }

    private fun consumed(count: Int) {
        remaining -= count
        if (remaining < 0L) {
            exceeded = true
            throw IOException("WebDAV listing byte limit reached")
        }
    }
}
