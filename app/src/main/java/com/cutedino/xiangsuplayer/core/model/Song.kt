package com.cutedino.xiangsuplayer.core.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String = "",
    val durationMs: Long = 0L
)
