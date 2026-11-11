package net.moriafly.ncm

import android.content.SharedPreferences
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.zip.GZIPOutputStream

class NcmBackendRegressionTest {
    @Test fun lateCookiesAndProfileCannotRestoreLoggedOutSession() {
        val session = NcmSession(fakePrefs())
        val original = session.snapshot()
        session.merge(original, listOf("MUSIC_U=opaque&signed=1", "__csrf=old"))
        session.saveProfile(original, 123L, "original")
        val prior = session.logout()
        assertEquals("opaque&signed=1", prior.cookies["MUSIC_U"])
        session.merge(prior, listOf("MUSIC_U=late"))
        assertThrows(IllegalStateException::class.java) { session.saveProfile(prior, 123L, "late") }
        assertFalse(session.isLogin)
        assertEquals(0L, session.userId)
    }

    @Test fun multipleRepliesMergeWithoutReplacingEachOther() {
        val session = NcmSession(fakePrefs())
        val state = session.snapshot()
        session.merge(state, listOf("first=one"))
        session.merge(state, listOf("second=two"))
        assertEquals(mapOf("first" to "one", "second" to "two"), session.cookies)
        session.merge(state, listOf("first=one; Max-Age=0"))
        assertEquals(mapOf("second" to "two"), session.cookies)
    }

    @Test fun anonymousFailureRetriesAndConcurrentCallersShareRegistration() = runBlocking {
        var clock = 0L
        val registration = AnonymousRegistration { clock }
        var ready = false
        var calls = 0
        registration.ensure(1, { ready }) { calls++; false }
        registration.ensure(1, { ready }) { calls++; true }
        assertEquals(1, calls)
        clock += 5_000_000_000L
        coroutineScope {
            repeat(5) {
                launch {
                    registration.ensure(1, { ready }) { calls++; delay(10); ready = true; true }
                }
            }
        }
        assertEquals(2, calls)
    }

    @Test fun httpBusinessAndMalformedResponsesAreDistinct() {
        assertThrows(NcmHttpException::class.java) { NcmRequest.parseResponse(503, "{}".toByteArray()) }
        assertThrows(IllegalStateException::class.java) { NcmRequest.parseResponse(200, "{} trailing".toByteArray()) }
        val response = NcmRequest.parseResponse(200, """{"code":301,"message":"login"}""".toByteArray())
        assertThrows(NcmBusinessException::class.java) { response.checkedBody() }
        val qr = NcmRequest.parseResponse(200, """{"code":801}""".toByteArray())
        assertEquals(801, qr.checkedBody(800, 801, 802, 803)["code"])
        assertTrue(NcmRequest.parseResponse(200, """{"code":"200","data":{}}""".toByteArray()).isSuccess)
    }

    @Test fun responseAndInflationAreBounded() {
        ResponseBody.create(MediaType.parse("application/json"), ByteArray(1025)).use {
            assertThrows(IOException::class.java) { it.readBounded(1024) }
        }
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { gzip -> repeat(16_385) { gzip.write(ByteArray(1024)) } }
        assertThrows(IOException::class.java) { NcmRequest.parseResponse(200, compressed.toByteArray()) }
    }

    private fun fakePrefs(): SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        val type = SharedPreferences::class.java
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "getString", "getLong" -> data[args[0]] ?: args[1]
                "edit" -> {
                    val editType = SharedPreferences.Editor::class.java
                    val changes = mutableMapOf<String, Any?>()
                    Proxy.newProxyInstance(editType.classLoader, arrayOf(editType)) { self, editMethod, editArgs ->
                        when (editMethod.name) {
                            "putString", "putLong" -> self.also { changes[editArgs[0] as String] = editArgs[1] }
                            "remove" -> self.also { changes[editArgs[0] as String] = null }
                            "apply" -> {
                                changes.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                                null
                            }
                            else -> error(editMethod.name)
                        }
                    }
                }
                else -> error(method.name)
            }
        } as SharedPreferences
    }
}
