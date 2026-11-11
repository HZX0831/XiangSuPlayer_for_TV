package com.cutedino.xiangsuplayer.core.audio

import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.LyricWord
import kotlin.math.abs

object LyricEngine {

    private val timeRegex = Regex("""\[(\d{1,4}):([0-5]\d)(?:[.:](\d{1,3}))?\]""")
    private val yrcLine = Regex("""^\[(\d{1,9}),(\d{1,9})\](.*)$""")
    private val yrcWord = Regex("""\((\d{1,9}),(\d{1,9}),\d+\)""")

    fun parseYrc(yrc: String, lrc: String, translation: String? = null): List<LyricLine> {
        val fallback = parse(lrc, translation)
        val translations = parseRawMap(translation.orEmpty())
        val lines = yrc.lineSequence().mapNotNull { raw ->
            val match = yrcLine.matchEntire(raw.trim()) ?: return@mapNotNull null
            val (startValue, durationValue, text) = match.destructured
            val start = startValue.toLong()
            val end = start + durationValue.toLong()
            val timings = yrcWord.findAll(text).toList()
            if (timings.isEmpty() || timings.first().range.first != 0) return@mapNotNull null
            val words = timings.mapIndexed { index, timing ->
                val wordStart = timing.groupValues[1].toLong()
                val wordEnd = wordStart + timing.groupValues[2].toLong()
                val limit = timings.getOrNull(index + 1)?.range?.first ?: text.length
                LyricWord(wordStart, wordEnd, text.substring(timing.range.last + 1, limit))
            }
            val lineText = words.joinToString("") { it.text }
            if (lineText.isBlank()) return@mapNotNull null
            val reliable = words.all { it.startMs >= start && it.endMs in (it.startMs + 1)..end } &&
                words.zipWithNext().all { (first, second) -> first.endMs <= second.startMs }
            LyricLine(start, lineText, translationFor(translations, start, lineText), words.takeIf { reliable }.orEmpty())
        }.sortedBy { it.timeMs }.distinctBy { it.timeMs }.toList()
        return lines.ifEmpty { fallback }
    }

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
            LyricLine(
                timeMs = timeMs,
                text = text,
                translation = translationFor(rawTranslation, timeMs, text)
            )
        }
    }

    private fun translationFor(lines: List<Pair<Long, String>>, timeMs: Long, text: String): String? =
        lines.minByOrNull { (time, _) -> abs(time - timeMs) }
            ?.takeIf { (time, _) -> abs(time - timeMs) <= 450L }
            ?.second?.takeIf { it.isNotBlank() && it != "//" && it != text }

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
                val ms = msStr.padEnd(3, '0').toLong()

                val totalMs = min * 60 * 1000 + sec * 1000 + ms
                result.add(totalMs to text)
            }
        }

        return result.sortedBy { it.first }
    }
}
