package com.cutedino.xiangsuplayer.data.auth

import org.junit.Assert.*
import org.junit.Test

class QqQrAttemptsTest {
    @Test fun manualBindingAndUnlinkPreventLateQrCompletion() {
        val attempts = QqQrAttempts()
        var account = ""
        val first = attempts.restart()
        assertTrue(attempts.publish(first, "first"))
        assertEquals(first, attempts.current("first"))
        attempts.invalidate { account = "manual" }
        assertFalse(attempts.complete(first) { account = "late" })
        assertEquals("manual", account)

        val second = attempts.restart()
        assertTrue(attempts.publish(second, "second"))
        attempts.invalidate { account = "" }
        assertFalse(attempts.complete(second) { account = "late" })
        assertEquals("", account)
    }

    @Test fun refreshingInvalidatesAnInFlightPrepareAndCompletesOnlyOnce() {
        val attempts = QqQrAttempts()
        val old = attempts.restart()
        val current = attempts.restart()
        assertFalse(attempts.publish(old, "old"))
        assertTrue(attempts.publish(current, "current"))
        var writes = 0
        assertTrue(attempts.complete(current) { writes++ })
        assertFalse(attempts.complete(current) { writes++ })
        assertEquals(1, writes)
    }
}
