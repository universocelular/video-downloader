package com.deniscerri.ytdl.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledYtdlpTest {
    private val bundled = "2026.10.26"

    @Test
    fun installsWhenNoCopyExists() {
        assertTrue(BundledYtdlp.shouldInstall(binaryExists = false, installedVersion = bundled, bundledVersion = bundled))
    }

    @Test
    fun replacesACopyWithUnknownVersion() {
        // Builds before this change copied the bundled yt-dlp without recording its version
        assertTrue(BundledYtdlp.shouldInstall(binaryExists = true, installedVersion = "", bundledVersion = bundled))
        assertTrue(BundledYtdlp.shouldInstall(binaryExists = true, installedVersion = null, bundledVersion = bundled))
    }

    @Test
    fun replacesAnOlderVersion() {
        assertTrue(BundledYtdlp.shouldInstall(binaryExists = true, installedVersion = "2026.08.19", bundledVersion = bundled))
    }

    @Test
    fun keepsTheSameOrANewerVersion() {
        assertFalse(BundledYtdlp.shouldInstall(binaryExists = true, installedVersion = bundled, bundledVersion = bundled))
        assertFalse(BundledYtdlp.shouldInstall(binaryExists = true, installedVersion = "2026.11.02", bundledVersion = bundled))
        assertFalse(BundledYtdlp.shouldInstall(binaryExists = true, installedVersion = "2026.10.26.233012", bundledVersion = bundled))
    }
}
