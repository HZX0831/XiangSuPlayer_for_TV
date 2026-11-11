package com.cutedino.xiangsuplayer.core.model

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList()
)

data class LyricWord(val startMs: Long, val endMs: Long, val text: String)
