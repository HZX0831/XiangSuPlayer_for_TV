package net.moriafly.ncm

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmSessionTest {
    @Test fun concurrentResponseCookiesDoNotOverwriteEachOther() {
        val values = ConcurrentHashMap<String, String>()
        val firstRead = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondApplied = CountDownLatch(1)
        val gate = java.util.concurrent.atomic.AtomicBoolean(false)
        val sp = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getLong" -> args!![1]
                "getString" -> {
                    val captured = values[args!![0]] ?: args[1]
                    if (gate.compareAndSet(true, false)) {
                        firstRead.countDown()
                        check(releaseFirst.await(3, TimeUnit.SECONDS))
                    }
                    captured
                }
                "edit" -> {
                    var pending: Pair<String, String>? = null
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, edit, params ->
                        when (edit.name) {
                            "putString" -> { pending = params!![0] as String to params[1] as String; proxy }
                            "apply" -> {
                                pending?.let { values[it.first] = it.second }
                                if (values["cookies"]?.contains("__csrf") == true) secondApplied.countDown()
                                null
                            }
                            else -> error("Unexpected editor call: ${edit.name}")
                        }
                    }
                }
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
        val session = NcmSession(sp)
        val state = session.snapshot()
        gate.set(true)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit { session.merge(state, listOf("MUSIC_U=login; Path=/")) }
            assertTrue(firstRead.await(2, TimeUnit.SECONDS))
            val second = pool.submit { secondStarted.countDown(); session.merge(state, listOf("__csrf=csrf")) }
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
            // Without a session lock the second response commits before the first,
            // then the first response overwrites it with its stale snapshot.
            secondApplied.await(500, TimeUnit.MILLISECONDS)
            releaseFirst.countDown()
            first.get(3, TimeUnit.SECONDS)
            second.get(3, TimeUnit.SECONDS)
            assertEquals(mapOf("MUSIC_U" to "login", "__csrf" to "csrf"), session.cookies)
        } finally {
            releaseFirst.countDown()
            pool.shutdownNow()
        }
    }
}
