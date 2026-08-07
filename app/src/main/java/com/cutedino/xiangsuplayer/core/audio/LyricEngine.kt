package com.cutedino.xiangsuplayer.core.audio

import com.cutedino.xiangsuplayer.core.model.LyricLine

object LyricEngine {

    private val timeRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")

    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()

        val lines = lrcContent.split("\n")
        val result = mutableListOf<LyricLine>()

        for (line in lines) {
            val matches = timeRegex.findAll(line)
            if (matches.count() == 0) continue

            val text = line.replace(timeRegex, "").trim()
            if (text.isEmpty()) continue

            for (match in matches) {
                val (minStr, secStr, msStr) = match.destructured
                val min = minStr.toLongOrNull() ?: 0L
                val sec = secStr.toLongOrNull() ?: 0L
                var ms = msStr.toLongOrNull() ?: 0L
                if (msStr.length == 2) ms *= 10

                val totalMs = min * 60 * 1000 + sec * 1000 + ms
                result.add(LyricLine(totalMs, text))
            }
        }

        return result.sortedBy { it.timeMs }
    }
}
