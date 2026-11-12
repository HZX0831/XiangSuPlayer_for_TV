package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.data.session.SessionManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

internal object SourceNetwork {
    private val legacyHosts = setOf(
        "175.27.166.236", "music.nxinxz.com", "search.kuwo.cn", "antiserver.kuwo.cn",
        "m.kuwo.cn", "lyrics.kugou.com", "mobilecdnbj.kugou.com"
    )

    fun httpsUrl(url: String): HttpUrl? = url.toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty()
    }

    fun allowsPlayback(value: String, allowLegacy: Boolean): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return httpsUrl(value) != null || allowLegacy && legacyAllowed(url)
    }

    fun configure(builder: OkHttpClient.Builder, allowLegacy: Boolean = true): OkHttpClient.Builder = builder
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.isHttps && (!allowLegacy || !legacyAllowed(request.url))) {
                throw SourcePolicyException("未启用此旧版 HTTP 音源")
            }
            chain.proceed(request)
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (request.url.isHttps) {
                chain.proceed(request)
            } else {
                if (!allowLegacy || !legacyAllowed(request.url)) throw SourcePolicyException("不受信任的 HTTP 音源")
                chain.proceed(request.newBuilder().removeHeader("Cookie").removeHeader("Authorization").build())
            }
        }

    private fun legacyAllowed(url: HttpUrl): Boolean =
        !url.isHttps && SessionManager.legacyHttpSourcesEnabled && url.host in legacyHosts && url.port == 80 &&
            url.username.isEmpty() && url.password.isEmpty()
}
