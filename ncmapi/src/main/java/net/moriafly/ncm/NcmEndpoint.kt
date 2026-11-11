package net.moriafly.ncm

import java.net.URL

internal object NcmEndpoint {
    private val hosts = setOf("music.163.com", "interface.music.163.com", "interface3.music.163.com")

    fun acceptsSession(url: URL): Boolean =
        url.protocol == "https" && url.host.lowercase() in hosts &&
            (url.port == -1 || url.port == 443) && url.userInfo == null

    fun redirect(from: URL, location: String): URL =
        URL(from, location).also { to ->
            require(acceptsSession(from) && acceptsSession(to) && from.host.equals(to.host, true)) {
                "Untrusted NetEase redirect"
            }
        }
}
