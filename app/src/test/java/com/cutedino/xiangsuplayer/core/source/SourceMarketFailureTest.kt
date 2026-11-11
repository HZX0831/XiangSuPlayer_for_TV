package com.cutedino.xiangsuplayer.core.source

import android.app.Application
import com.cutedino.xiangsuplayer.core.source.market.SourceMarketRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SourceMarketFailureTest {
    private fun client(code: Int, body: String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("fixture").body(body.toResponseBody()).build()
    }.build()

    @Test fun httpAndMalformedResponsesRemainFailuresInsteadOfEmptyMarkets() = runBlocking {
        assertTrue(SourceMarketRepository.fetchMarketReleases(client(403, "[]")).isFailure)
        assertTrue(SourceMarketRepository.fetchMarketReleases(client(200, "not-json")).isFailure)
        val unavailable = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
        assertTrue(SourceMarketRepository.fetchMarketReleases(unavailable).isFailure)
    }

    @Test fun validEmptyMarketRemainsAnEmptySuccess() = runBlocking {
        assertEquals(emptyList<Any>(), SourceMarketRepository.fetchMarketReleases(client(200, "[]")).getOrThrow())
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotPresentedAsMarketFailure() = runBlocking {
        SourceMarketRepository.fetchMarketReleases(Call.Factory { throw CancellationException("screen closed") })
        Unit
    }
}
