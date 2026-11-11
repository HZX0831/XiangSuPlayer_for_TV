package com.cutedino.xiangsuplayer.core.source

/**
 * The one rule for whether a sound source takes part in stream racing, shared by the source
 * market UI and [SoundSourceRepository] so the toggle shown is the toggle applied.
 */
object SourceEnablement {
    const val LX_PREFIX = "lx_"

    fun isLx(sourceId: String): Boolean = sourceId.startsWith(LX_PREFIX)

    /**
     * Built-in sources are opt-in through [enabledIds]. Installed LX scripts are on by default and
     * opt-out through [disabledLxIds], so scripts installed before the toggle existed stay enabled.
     */
    fun isEnabled(sourceId: String, enabledIds: Set<String>, disabledLxIds: Set<String>): Boolean =
        if (isLx(sourceId)) sourceId !in disabledLxIds else sourceId in enabledIds
}
