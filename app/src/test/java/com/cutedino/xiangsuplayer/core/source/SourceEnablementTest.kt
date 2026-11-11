package com.cutedino.xiangsuplayer.core.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceEnablementTest {
    private val enabled = setOf("netease_native", "ikun")

    @Test
    fun builtInSourcesFollowEnabledSet() {
        assertTrue(SourceEnablement.isEnabled("ikun", enabled, emptySet()))
        assertFalse(SourceEnablement.isEnabled("xinghai", enabled, emptySet()))
    }

    @Test
    fun lxSourcesDefaultToEnabled() {
        assertTrue(SourceEnablement.isEnabled("lx_custom", enabled, emptySet()))
    }

    @Test
    fun lxSourcesCanBeDisabled() {
        assertFalse(SourceEnablement.isEnabled("lx_custom", enabled, setOf("lx_custom")))
        assertTrue(SourceEnablement.isEnabled("lx_other", enabled, setOf("lx_custom")))
    }

    @Test
    fun lxDisabledListDoesNotAffectBuiltIns() {
        assertTrue(SourceEnablement.isEnabled("ikun", enabled, setOf("ikun")))
    }
}
