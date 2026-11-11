package com.cutedino.xiangsuplayer.core.phone

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal class PhoneInputServer(
    val address: Inet4Address,
    private val onInput: suspend (String) -> Unit,
    private val search: (suspend (String) -> List<PhoneChoice>)? = null,
    private val order: (suspend (PhoneChoice) -> Boolean)? = null,
    private val sessionDurationMs: Long = DURATION_MS,
    private val requestTimeoutMs: Long = 5_000L
) : Closeable {
    init {
        require(address.isSiteLocalAddress || address.isLoopbackAddress)
        require(sessionDurationMs in 1L..DURATION_MS && requestTimeoutMs in 1L..10_000L)
    }

    private val server = ServerSocket(0, 8, address)
    private val gson = Gson()
    private val expiresAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(sessionDurationMs)
    private val host = "${address.hostAddress}:${server.localPort}"
    private val token = ByteArray(24).apply { SecureRandom().nextBytes(this) }
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    private val path = "/$token"
    val url = "http://$host$path"
    val clientUrl: String get() = "$url/client"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private val clientSlots = Semaphore(4)
    private val searchLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var choices = emptyMap<String, PhoneChoice>()

    init {
        scope.launch {
            delay(sessionDurationMs)
            close()
        }
        scope.launch {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    if (!clientSlots.tryAcquire()) {
                        socket.close()
                        continue
                    }
                    clients.add(socket)
                    if (server.isClosed) {
                        clients.remove(socket)
                        socket.close()
                        break
                    }
                    launch {
                        try {
                            socket.use {
                                try {
                                    handle(it)
                                } catch (_: SocketTimeoutException) {
                                    respond(it, "408 Request Timeout", "request_timeout")
                                } catch (_: IllegalArgumentException) {
                                    respond(it, "400 Bad Request", "invalid_request")
                                }
                            }
                        } catch (_: Exception) {
                            // A disconnected client has no effect on the pairing session.
                        } finally {
                            clients.remove(socket)
                            clientSlots.release()
                        }
                    }
                }
            } catch (_: java.io.IOException) {
                close()
            }
        }
    }

    private suspend fun handle(socket: Socket) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(requestTimeoutMs)
        val input = BufferedInputStream(socket.getInputStream())
        var appliedTimeout = 0
        fun checkDeadline() {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) throw SocketTimeoutException("Pairing request deadline")
            val timeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceIn(1L, 3000L).toInt()
            if (timeout != appliedTimeout) {
                socket.soTimeout = timeout
                appliedTimeout = timeout
            }
        }
        fun line(limit: Int): String? = readLine(input, limit, ::checkDeadline)

        val request = line(2048)?.split(' ') ?: return
        if (request.size != 3 || request[2] != "HTTP/1.1") return
        val headers = mutableMapOf<String, String>()
        var headerBytes = 0
        while (true) {
            val header = line(2048) ?: return
            headerBytes += header.length + 2
            if (headerBytes > 8192) return
            if (header.isEmpty()) break
            val separator = header.indexOf(':')
            if (separator <= 0) return
            val key = header.substring(0, separator).lowercase(Locale.ROOT)
            if (headers.put(key, header.substring(separator + 1).trim()) != null) return
        }

        val method = request[0]
        if (method == "GET" && request[1] == "$path/client") {
            if (System.nanoTime() - expiresAt >= 0L || !allows(host, "$path/client", method, request[1], headers["host"], headers["origin"])) {
                respond(socket, "403 Forbidden", "invalid_pairing")
            } else respondClient(socket)
            return
        }
        if (System.nanoTime() - expiresAt >= 0L ||
            !allows(host, path, method, request[1], headers["host"], headers["origin"])) {
            respond(socket, "403 Forbidden", "invalid_pairing")
            return
        }
        if (method == "GET") {
            respond(socket, "200 OK", "ready", mapOf("mode" to if (search == null) "input" else "order"))
            return
        }
        val length = headers["content-length"]?.toIntOrNull()
        if (length == null || length !in 1..4096 || "transfer-encoding" in headers ||
            headers["content-type"]?.substringBefore(';') != "application/x-www-form-urlencoded") {
            respond(socket, "400 Bad Request", "invalid_body")
            return
        }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < bytes.size) {
            checkDeadline()
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) return
            offset += count
        }
        if (System.nanoTime() - expiresAt >= 0L) {
            respond(socket, "403 Forbidden", "expired_pairing")
            return
        }
        val body = bytes.toString(Charsets.UTF_8)
        val key = body.substringBefore('=')
        val value = URLDecoder.decode(body.substringAfter('=', ""), "UTF-8").trim()
        if ('&' in body || value.length !in 1..160) {
            respond(socket, "400 Bad Request", "invalid_input")
            return
        }

        when {
            key == "choice" && order != null -> {
                val choice = choices[value]
                val accepted = choice?.let { withTimeoutOrNull(6000L) { order.invoke(it) } }
                when (accepted) {
                    true -> respond(socket, "200 OK", "ordered")
                    false -> respond(socket, "409 Conflict", "queue_unavailable")
                    null -> if (choice == null) respond(socket, "409 Conflict", "expired_choice")
                        else respond(socket, "503 Service Unavailable", "order_timeout")
                }
            }
            key == "query" && search != null -> {
                if (!searchLock.tryLock()) {
                    respond(socket, "429 Too Many Requests", "search_busy")
                    return
                }
                try {
                    choices = emptyMap()
                    val results = withTimeoutOrNull(6000L) { search.invoke(value) }
                    if (results == null) respond(socket, "503 Service Unavailable", "search_timeout")
                    else {
                        choices = results.take(18).associateBy { UUID.randomUUID().toString() }
                        val entries = choices.map { (id, choice) -> mapOf("id" to id, "label" to choice.label.take(240)) }
                        respond(socket, "200 OK", "results", mapOf("choices" to entries))
                    }
                } finally {
                    searchLock.unlock()
                }
            }
            key == "query" -> {
                if (withTimeoutOrNull(3000L) { onInput(value); true } == true) {
                    respond(socket, "200 OK", "received")
                } else respond(socket, "503 Service Unavailable", "input_timeout")
            }
            else -> respond(socket, "400 Bad Request", "unknown_operation")
        }
    }

    private fun respond(socket: Socket, status: String, code: String, fields: Map<String, Any> = emptyMap()) {
        val bytes = gson.toJson(fields + ("status" to code)).toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    private fun respondClient(socket: Socket) {
        val bytes = PhonePairingPage.html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: " + bytes.size + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    override fun close() {
        runCatching { server.close() }
        scope.cancel()
        clients.forEach { runCatching { it.close() } }
    }

    companion object {
        const val DURATION_MS = 300_000L

        fun activeNetwork(context: Context): PairingNetwork? {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val (identity, addresses) = if (Build.VERSION.SDK_INT >= 23) {
                val network = manager.activeNetwork ?: return null
                network.toString() to manager.getLinkProperties(network)?.linkAddresses.orEmpty().map { it.address }
            } else {
                val network = manager.activeNetworkInfo?.takeIf { it.isConnected } ?: return null
                network.type.toString() to NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }.flatMap { it.inetAddresses.toList() }
            }
            return addresses.filterIsInstance<Inet4Address>().firstOrNull {
                it.isSiteLocalAddress && !it.isLoopbackAddress
            }?.let { PairingNetwork(it, identity) }
        }

        fun allows(host: String, path: String, method: String, target: String, suppliedHost: String?, origin: String?): Boolean {
            if (target != path || suppliedHost != host) return false
            return when (method) {
                "GET" -> origin == null || origin == "http://$host"
                "POST" -> origin == "http://$host"
                else -> false
            }
        }

        private fun readLine(input: BufferedInputStream, limit: Int, beforeRead: () -> Unit): String? {
            val bytes = ByteArrayOutputStream()
            while (bytes.size() < limit) {
                beforeRead()
                val byte = input.read()
                if (byte < 0) return null
                if (byte == 10) return bytes.toString("US-ASCII").removeSuffix("\r")
                bytes.write(byte)
            }
            return null
        }

    }
}

internal data class PhoneChoice(val label: String, val song: com.cutedino.xiangsuplayer.core.model.Song)
internal data class PairingNetwork(val address: Inet4Address, val identity: String)
