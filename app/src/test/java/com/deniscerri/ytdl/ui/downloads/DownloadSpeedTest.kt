package com.deniscerri.ytdl.ui.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DownloadSpeedTest {
    private val spanish = Locale.forLanguageTag("es-AR")

    @Test
    fun readsSpeedAndTimeLeft() {
        assertEquals(
            DownloadSpeed("3,2 MB/s", "12 s"),
            DownloadSpeed.parse("[download]  72.0% of  120.53MiB at    3.20MiB/s ETA 00:12", spanish),
        )
        assertEquals(
            DownloadSpeed("850 KB/s", "2 min"),
            DownloadSpeed.parse("[download]  10.5% of ~ 80.00MiB at  850.00KiB/s ETA 01:40 (frag 3/40)", spanish),
        )
        assertEquals(
            DownloadSpeed("1.5 MB/s", "1 h 5 min"),
            DownloadSpeed.parse("[download]   1.0% of 6.00GiB at 1.50MiB/s ETA 1:05:09", Locale.US),
        )
    }

    @Test
    fun unknownValuesShowNothing() {
        assertNull(DownloadSpeed.parse("[download]   0.0% of  120.53MiB at  Unknown B/s ETA Unknown", spanish))
        assertNull(DownloadSpeed.parse("[download] 100% of  120.53MiB in 00:00:37 at 3.20MiB/s", spanish))
        assertNull(DownloadSpeed.parse("[Merger] Merging formats into \"video.mp4\"", spanish))
    }
}
