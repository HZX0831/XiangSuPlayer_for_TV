package com.cutedino.xiangsuplayer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricEngineTest {
    @Test fun wordTimingKeepsSpacesAndAlignsTranslation() {
        val line = LyricEngine.parseYrc(
            "[1000,2200](1000,500,0)逐字 (1500,1700,0)歌词",
            "[00:01]备用", "[00:01.2]Translation"
        ).single()
        assertEquals("逐字 歌词", line.text)
        assertEquals("Translation", line.translation)
        assertEquals(listOf(1000L, 1500L), line.words.map { it.startMs })
    }

    @Test fun malformedTimingKeepsLineWithoutWordHighlight() {
        val lines = LyricEngine.parseYrc("[800,200](850,400,0)时间超出", "")
        assertTrue(lines.single().words.isEmpty())
        assertEquals("时间超出", lines.single().text)
        assertEquals(listOf(1100L, 2000L), LyricEngine.parseYrc("invalid", "[0:01.1][00:02]备用").map { it.timeMs })
    }
}
