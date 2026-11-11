package net.moriafly.ncm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.URL

class NcmEndpointTest {
    private val origin = URL("https://music.163.com/weapi/test")

    @Test fun relativeRedirectStaysOnOrigin() {
        assertEquals(URL("https://music.163.com/weapi/next"), NcmEndpoint.redirect(origin, "next"))
    }

    @Test fun redirectsCannotForwardSessionToAnotherOrigin() {
        for (location in listOf(
            "//other.example/test", "http://music.163.com/test",
            "https://interface3.music.163.com/test", "https://music.163.com:8443/test"
        )) {
            assertThrows(IllegalArgumentException::class.java) { NcmEndpoint.redirect(origin, location) }
        }
    }

    @Test fun craftedTargetsCannotReceiveSession() {
        assertFalse(NcmEndpoint.acceptsSession(URL("https://music.163.com.other.example/test")))
        assertFalse(NcmEndpoint.acceptsSession(URL("https://other.example@music.163.com/test")))
    }
}
