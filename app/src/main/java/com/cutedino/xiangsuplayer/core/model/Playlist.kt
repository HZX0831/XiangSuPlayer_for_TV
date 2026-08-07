package com.cutedino.xiangsuplayer.core.model

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val description: String = "",
    val playCount: Long = 0L,
    val trackCount: Int = 0
)
