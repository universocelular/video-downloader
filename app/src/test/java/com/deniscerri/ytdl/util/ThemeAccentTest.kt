package com.deniscerri.ytdl.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeAccentTest {
    @Test
    fun eachAccentKeepsItsChoice() {
        for (accent in ThemeUtil.Accent.entries) {
            assertEquals(accent, ThemeUtil.Accent.fromKey(accent.key))
        }
    }

    @Test
    fun accentsFromOlderBuildsBecomeViolet() {
        for (old in listOf("blue", "red", "green", "purple", "yellow", "monochrome", "Default", "", null)) {
            assertEquals(ThemeUtil.Accent.Violet, ThemeUtil.Accent.fromKey(old))
        }
    }
}
