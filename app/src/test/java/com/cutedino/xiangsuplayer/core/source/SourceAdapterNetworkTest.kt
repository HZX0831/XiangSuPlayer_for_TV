package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.toplist.ToplistRepository
import kotlinx.coroutines.runBlocking
import net.moriafly.ncm.ResponseLimitException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SourceAdapterNetworkTest {
    private val song = Song("123", "Track", "Artist", "Album", source = "wy")

    @Test fun ikunDisablesRedirectsEvenWhenTheBaseClientFollowsThem() {
        val adapter = IKunSourceAdapter(OkHttpClient.Builder().followRedirects(true).build())
        val transport = adapter.javaClass.getDeclaredField("okHttpClient").apply { isAccessible = true }
            .get(adapter) as OkHttpClient
        assertFalse(transport.followRedirects)
    }

    @Test fun templateResolvesRelativeLocationWithoutDownloadingTheMedia() = runBlocking {
        var requests = 0
        val template = template(302, "../track.mp3") { requests++ }
        val result = template.getStreamUrl(song, AudioQuality.STANDARD).getOrThrow()
        assertEquals("https://source.example/track.mp3", result.streamUrl)
        assertEquals(1, requests)
    }

    @Test fun xingHaiTriesBackupAfterFirstLinkIsRejected() = runBlocking {
        val seen = mutableListOf<String>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            seen.add(host)
            val payload = if (host == "api.chksz.top") {
                """{"code":200,"data":{"url":"http://untrusted.example/music.mp3"}}"""
            } else {
                """{"url":"https://backup.example/music.mp3"}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("fixture").body(payload.toResponseBody()).build()
        }.build()
        val result = XingHaiSourceAdapter(transport).getStreamUrl(song, AudioQuality.STANDARD).getOrThrow()
        assertEquals("https://backup.example/music.mp3", result.streamUrl)
        assertEquals(listOf("api.chksz.top", "music-api.gdstudio.xyz"), seen)
    }

    @Test fun templateRejectsUnsafeLocation() = runBlocking {
        val result = template(302, "http://other.example/track.mp3").getStreamUrl(song, AudioQuality.STANDARD)
        assertTrue(result.exceptionOrNull() is SourcePolicyException)
    }

    @Test fun onlyTransportFailuresAndServerErrorsContributeToCooldown() = runBlocking {
        for ((status, expected) in listOf(404 to false, 503 to true)) {
            val failure = template(status).getStreamUrl(song, AudioQuality.STANDARD).exceptionOrNull()
            assertTrue(failure is SourceHttpException)
            assertEquals(expected, isTransportFailure(checkNotNull(failure)))
        }
        assertTrue(isTransportFailure(IOException("offline")))
        assertFalse(isTransportFailure(ResponseLimitException(128)))
    }

    @Test fun boardRequestDistinguishesHttpFailureFromAnEmptySuccessfulBody() = runBlocking {
        val request = Request.Builder().url("https://source.example/board").build()
        val failure = runCatching { ToplistRepository.execute(request, client(503)) }.exceptionOrNull()
        assertEquals(503, (failure as SourceHttpException).status)
        assertEquals("[]", ToplistRepository.execute(request, client(200, body = "[]")))
    }

    private fun template(status: Int, location: String? = null, called: () -> Unit = {}) =
        UrlTemplateSourceAdapter(
            "test", "Template", "https://source.example/resolve/{id}", 1,
            client(status, location, called = called)
        )

    private fun client(status: Int, location: String? = null, body: String = "", called: () -> Unit = {}) =
        OkHttpClient.Builder().addInterceptor { chain ->
            called()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture").body(body.toResponseBody())
                .apply { if (location != null) header("Location", location) }.build()
        }.build()
}
