package com.cutedino.xiangsuplayer.core.phone

import com.cutedino.xiangsuplayer.core.model.Song
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class PhoneInputServerTest {
    @Test fun pairingRequiresExpectedHostTokenAndOrigin() {
        val host = "192.168.1.1:3021"
        val path = "/random-token"
        assertTrue(PhoneInputServer.allows(host, path, "GET", path, host, null))
        assertTrue(PhoneInputServer.allows(host, path, "POST", path, host, "http://$host"))
        assertFalse(PhoneInputServer.allows(host, path, "POST", path, host, "http://attacker.example"))
        assertFalse(PhoneInputServer.allows(host, path, "POST", path, host, null))
        assertFalse(PhoneInputServer.allows(host, path, "GET", "/expired", host, null))
        assertFalse(PhoneInputServer.allows(host, path, "GET", path, "attacker.example", null))
    }

    @Test fun serverRejectsOversizedInputAndReceivesChineseQuery() {
        val query = AtomicReference("")
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        PhoneInputServer(address, onInput = { query.set(it) }).use { server ->
            val url = URI(server.url)
            assertEquals(49, url.path.length)
            assertTrue(post(url, "query=hello", "http://other.example").startsWith("HTTP/1.1 403"))
            assertTrue(post(url, "query=" + "a".repeat(4200)).startsWith("HTTP/1.1 400"))
            assertEquals("", query.get())
            assertTrue(post(url, "query=" + URLEncoder.encode("测试歌曲", "UTF-8")).startsWith("HTTP/1.1 200"))
            assertEquals("测试歌曲", query.get())
        }
    }

    @Test fun slowHeaderHasAbsoluteDeadline() {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        PhoneInputServer(address, onInput = {}, requestTimeoutMs = 400L).use { server ->
            val url = URI(server.url)
            Socket(url.host, url.port).use { client ->
                client.soTimeout = 2500
                val drip = thread {
                    runCatching {
                        repeat(24) {
                            client.getOutputStream().write('G'.code)
                            client.getOutputStream().flush()
                            Thread.sleep(70)
                        }
                    }
                }
                try {
                    assertTrue(client.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 408"))
                } finally {
                    client.close()
                    drip.join(2500)
                }
            }
        }
    }

    @Test fun serverExpiresWithoutCallerClosingIt() {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        PhoneInputServer(address, onInput = {}, sessionDurationMs = 100L).use { server ->
            val url = URI(server.url)
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline) {
                try {
                    Socket(url.host, url.port).use { }
                } catch (_: ConnectException) {
                    return
                }
                Thread.sleep(40L)
            }
            assertThrows(ConnectException::class.java) { Socket(url.host, url.port).close() }
        }
    }

    @Test fun clientPagePreservesTheJsonEndpointAndRejectsForeignOrigins() {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        PhoneInputServer(address, onInput = {}).use { server ->
            fun get(uri: URI, origin: String? = null): String = Socket(uri.host, uri.port).use { socket ->
                socket.soTimeout = 4500
                val originHeader = origin?.let { "Origin: " + it + "\r\n" }.orEmpty()
                socket.getOutputStream().write(("GET " + uri.path + " HTTP/1.1\r\nHost: " + uri.authority + "\r\n" + originHeader + "\r\n").toByteArray())
                socket.getInputStream().bufferedReader().readText()
            }
            val api = get(URI(server.url))
            assertTrue(api.contains("application/json"))
            assertTrue(api.contains("ready"))
            val client = get(URI(server.clientUrl))
            assertTrue(client.contains("text/html"))
            assertTrue(client.contains("<form"))
            assertTrue(client.contains("connect-src 'self'"))
            assertTrue(get(URI(server.clientUrl), "http://other.example").startsWith("HTTP/1.1 403"))
            assertTrue(get(URI(server.clientUrl + "/extra")).startsWith("HTTP/1.1 403"))
        }
    }

    @Test fun searchesRetainIndependentChoicesAndDistinguishServiceFailureFromEmpty() {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        val ordered = mutableListOf<String>()
        PhoneInputServer(address, onInput = {}, search = { query ->
            when (query) {
                "failure" -> throw java.io.IOException("upstream unavailable")
                "empty" -> emptyList()
                else -> listOf(PhoneChoice(query, Song(query, query, "Artist", "")))
            }
        }, order = {
            synchronized(ordered) { ordered.add(it.song.id) }
            true
        }).use { server ->
            val url = URI(server.url)
            fun choice(query: String): String {
                val reply = post(url, "query=$query")
                assertTrue(reply.startsWith("HTTP/1.1 200"))
                return JsonParser.parseString(reply.substringAfter("\r\n\r\n")).asJsonObject
                    .getAsJsonArray("choices")[0].asJsonObject["id"].asString
            }
            val a = choice("phoneA")
            val b = choice("phoneB")
            val failure = post(url, "query=failure")
            assertTrue(failure.startsWith("HTTP/1.1 503"))
            assertTrue(failure.contains("search_failed"))
            val empty = post(url, "query=empty")
            assertTrue(empty.startsWith("HTTP/1.1 200"))
            assertTrue(empty.contains("\"choices\":[]"))
            assertTrue(post(url, "choice=$a").startsWith("HTTP/1.1 200"))
            assertTrue(post(url, "choice=$b").startsWith("HTTP/1.1 200"))
            assertEquals(listOf("phoneA", "phoneB"), synchronized(ordered) { ordered.toList() })
        }
    }

    private fun post(url: URI, data: String, origin: String = "http://${url.authority}"): String =
        Socket(url.host, url.port).use { socket ->
            socket.soTimeout = 4500
            socket.getOutputStream().write(
                ("POST ${url.path} HTTP/1.1\r\nHost: ${url.authority}\r\nOrigin: $origin\r\n" +
                    "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${data.length}\r\n\r\n$data")
                    .toByteArray(Charsets.US_ASCII)
            )
            socket.getInputStream().bufferedReader().readText()
        }
}
