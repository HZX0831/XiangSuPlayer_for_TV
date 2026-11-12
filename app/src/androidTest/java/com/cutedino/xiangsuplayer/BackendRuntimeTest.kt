package com.cutedino.xiangsuplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cutedino.xiangsuplayer.core.audio.PlaybackService
import com.cutedino.xiangsuplayer.data.db.AppDatabase
import com.cutedino.xiangsuplayer.ui.main.MainActivity
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BackendRuntimeTest {
    @Test fun applicationStartsMediaServiceAndOpensRoomWithoutActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertTrue(context is App)
        assertEquals("com.cutedino.xiangsuplayer", context.packageName)
        val binder = AtomicReference<IBinder>()
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder.set(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val intent = Intent(context, PlaybackService::class.java)
            .setAction("com.cutedino.xiangsuplayer.BIND_PLAYBACK")
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("Media service did not bind", connected.await(10, TimeUnit.SECONDS))
            assertNotNull(binder.get())
            runBlocking {
                val dao = AppDatabase.getInstance().songDao()
                dao.getAllSongs()
                dao.getPlayHistoryList()
            }
        } finally {
            context.unbindService(connection)
        }
    }

    @Test fun mediaSessionActivityReturnsToPlayer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val main = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val monitor = instrumentation.addMonitor(PlayerActivity::class.java.name, null, false)
        val session = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, session)
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()
        try {
            val controller = controllerFuture.get(10, TimeUnit.SECONDS)
            instrumentation.runOnMainSync { checkNotNull(controller.sessionActivity).send() }
            val player = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertTrue("Media session did not open PlayerActivity", player is PlayerActivity)
            instrumentation.runOnMainSync { player.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync {
                MediaController.releaseFuture(controllerFuture)
                main.finish()
            }
        }
    }
}
