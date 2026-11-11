package com.cutedino.xiangsuplayer.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebDavClientTest {
    private val root = checkNotNull(WebDavClient.root("https://music.example.test:8443/dav/Music"))

    @Test fun childRemainsInsideHttpsRoot() {
        assertEquals("/dav/Music/album/song.m4a", WebDavClient.child(root, root, "album/song.m4a")?.encodedPath)
        for (href in listOf(
            "http://music.example.test:8443/dav/Music/song.m4a",
            "//elsewhere.example.test/dav/Music/song.m4a",
            "https://music.example.test/dav/Music/song.m4a",
            "https://user:password@music.example.test:8443/dav/Music/song.m4a",
            "/dav/Music-copy/song.m4a", "../song.m4a", "%2e%2e/song.m4a",
            "a%2f..%2fsong.m4a", "/dav/Music/song.m4a?token=123"
        )) assertNull(href, WebDavClient.child(root, root, href))
    }

    @Test fun rangeProbeRequiresActualSingleByte206() {
        assertTrue(WebDavClient.validRange(206, "bytes 0-0/4096"))
        assertFalse(WebDavClient.validRange(200, "bytes 0-0/4096"))
        for (value in listOf(null, "bytes 0-0/*", "bytes 1-1/4096", "bytes 0-1/4096", "bytes 0-0/0")) {
            assertFalse(WebDavClient.validRange(206, value))
        }
    }

    @Test fun unsupportedRangeDegradesButAuthorizationRemainsAnError() {
        assertFalse(WebDavClient.rangeSupport(416, "bytes */4096"))
        assertFalse(WebDavClient.rangeSupport(501, null))
        assertTrue(WebDavClient.rangeSupport(206, "bytes 0-0/4096"))
        assertThrows(IllegalStateException::class.java) { WebDavClient.rangeSupport(401, null) }
    }

    @Test fun largeMultistatusIsParsedAndItemLimitReturnsPartialPage() {
        val xml = buildString {
            append("""<d:multistatus xmlns:d="DAV:">""")
            repeat(16_001) { i ->
                append("""<d:response><d:href>/dav/Music/track-$i.mp3</d:href>
                    <d:propstat><d:prop><d:displayname>Track $i</d:displayname><d:resourcetype/>
                    </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""")
            }
            append("</d:multistatus>")
        }.toByteArray()
        assertTrue(xml.size > 2_097_152)
        val listing = WebDavClient.parse(xml.inputStream(), root, root)
        assertTrue(listing.incomplete)
        assertEquals(16_000, listing.items.size)
        assertEquals("Track 0", listing.items.first().name)
    }

    @Test fun pagesShareSnapshotUntilExpiryOrInvalidation() = runBlocking {
        var clock = 0L
        val cache = FolderListingCache<List<Int>>({ clock }, ttlNanos = 10)
        var reads = 0
        suspend fun page() = cache.get("folder", "dir") { reads++; listOf(1, 2, 3) }
        assertEquals(page(), page())
        assertEquals(1, reads)
        clock = 10L
        page()
        assertEquals(2, reads)
        cache.invalidate("folder")
        page()
        assertEquals(3, reads)
    }
}
