package com.cutedino.xiangsuplayer.core.model

enum class AudioQuality(val displayName: String, val level: String) {
    STANDARD("标准音质", "standard"),
    HIGH("极高音质", "higher"),
    LOSSLESS("无损 FLAC", "lossless"),
    HIRES("Hi-Res 无损", "hires")
}
