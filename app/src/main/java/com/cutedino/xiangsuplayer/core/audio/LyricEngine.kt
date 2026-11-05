package com.cutedino.xiangsuplayer.core.audio

import com.cutedino.xiangsuplayer.core.model.LyricLine
import kotlin.math.abs

object LyricEngine {

    private val timeRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")

    fun parse(lrcContent: String, tlyricContent: String? = null): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()

        val rawOriginal = parseRawMap(lrcContent)
        if (rawOriginal.isEmpty()) return emptyList()

        val rawTranslation = if (!tlyricContent.isNullOrBlank()) {
            parseRawMap(tlyricContent)
        } else {
            emptyList()
        }

        return rawOriginal.map { (timeMs, text) ->
            val matchedTrans = rawTranslation.firstOrNull { (tTime, _) ->
                abs(tTime - timeMs) <= 450L
            }?.second?.takeIf { it.isNotBlank() && it != "//" && it != text }
            LyricLine(
                timeMs = timeMs,
                text = text,
                translation = matchedTrans
            )
        }
    }

    private fun parseRawMap(content: String): List<Pair<Long, String>> {
        val lines = content.split("\n")
        val result = mutableListOf<Pair<Long, String>>()

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
                result.add(totalMs to text)
            }
        }

        return result.sortedBy { it.first }
    }
}
