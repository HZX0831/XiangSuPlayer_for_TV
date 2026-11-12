package com.cutedino.xiangsuplayer.core.source

import android.app.Application
import org.robolectric.RuntimeEnvironment
import com.cutedino.xiangsuplayer.core.source.market.SourceMarketRepository
import com.cutedino.xiangsuplayer.data.session.SessionManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SourceIndexRecoveryTest {
    @Test fun badPrimaryFallsBackToValidatedIndex() {
        val context = RuntimeEnvironment.getApplication()
        SessionManager.init(context)
        val sourceDir = File(context.filesDir, "lx_sources").apply { mkdirs() }
        File(sourceDir, "good.js").writeText("/*! \n * @name Recovered\n * @apiBaseUrl https://source.test/api\n */")
        File(sourceDir, "installed.json").writeText("{\"bad\":")
        File(sourceDir, "installed.last-good.json").writeText("""{"good.js":"good.js"}""")
        SourceMarketRepository.init(context)
        assertEquals("good.js", SourceMarketRepository.getInstalledSources().single().fileName)
    }

    @Test fun badPrimaryAndBackupDoNotAbortInitialization() {
        val context = RuntimeEnvironment.getApplication()
        SessionManager.init(context)
        val sourceDir = File(context.filesDir, "lx_sources").apply { mkdirs() }
        File(sourceDir, "installed.json").writeText("""{"outside.js":"../outside.js"}""")
        File(sourceDir, "installed.last-good.json").writeText("[broken")
        SourceMarketRepository.init(context)
        assertTrue(SourceMarketRepository.getInstalledSources().isEmpty())
    }

    @Test fun explicitlyEmptyBuiltInSourceSetSurvivesReload() {
        val context = RuntimeEnvironment.getApplication()
        SessionManager.init(context)
        assertTrue(SessionManager.enabledSoundSources.isNotEmpty())
        SessionManager.enabledSoundSources = emptySet()
        SessionManager.init(context)
        assertTrue(SessionManager.enabledSoundSources.isEmpty())
    }
}
