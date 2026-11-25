package com.deniscerri.ytdl.ui.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadErrorReasonTest {
    private fun assertReason(expected: DownloadErrorReason, log: String) = assertEquals(log, expected, DownloadErrorReason.of(log))

    @Test
    fun signInAndBotChecksAskToSignIn() {
        assertReason(DownloadErrorReason.SignIn, "ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser or --cookies for the authentication.")
        assertReason(DownloadErrorReason.SignIn, "ERROR: [youtube] abc: Private video. Sign in if you've been granted access to this video")
        assertReason(DownloadErrorReason.SignIn, "ERROR: [Instagram] abc: Requested content is not available, rate-limit reached or login required.")
    }

    @Test
    fun onlyTheLastErrorCounts() {
        val log = """
            [youtube] Extracting URL: https://www.youtube.com/watch?v=abc
            WARNING: [youtube] No supported JavaScript runtime could be found. Pass cookies to avoid throttling.
            ERROR: [download] Got error: The read operation timed out
        """.trimIndent()
        assertReason(DownloadErrorReason.Network, log)
    }

    @Test
    fun httpStatusesAreTold() {
        assertReason(DownloadErrorReason.Unavailable, "ERROR: [generic] Unable to download webpage: HTTP Error 404: Not Found")
        assertReason(DownloadErrorReason.Forbidden, "ERROR: unable to download video data: HTTP Error 403: Forbidden")
        assertReason(DownloadErrorReason.RateLimit, "ERROR: [youtube] abc: HTTP Error 429: Too Many Requests")
        assertReason(DownloadErrorReason.Network, "ERROR: Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>")
    }

    @Test
    fun specificCausesWinOverGenericWords() {
        assertReason(DownloadErrorReason.Format, "ERROR: [youtube] abc: Requested format is not available. Use --list-formats for a list of available formats")
        assertReason(DownloadErrorReason.Geo, "ERROR: [youtube] abc: The uploader has not made this video available in your country")
        assertReason(DownloadErrorReason.Unavailable, "ERROR: [youtube] abc: Video unavailable. This video has been removed by the uploader")
        assertReason(DownloadErrorReason.Unsupported, "ERROR: Unsupported URL: https://example.com/login")
        assertReason(DownloadErrorReason.NotStarted, "ERROR: [youtube] abc: Premieres in 2 hours")
        assertReason(DownloadErrorReason.Storage, "ERROR: unable to write data: [Errno 28] No space left on device")
    }

    @Test
    fun nothingToReadIsUnknown() {
        assertReason(DownloadErrorReason.Unknown, "")
        assertEquals(DownloadErrorReason.Unknown, DownloadErrorReason.of(null))
        assertReason(DownloadErrorReason.Unknown, "ERROR: [facebook] abc: Cannot parse data; please report this issue on GitHub")
    }
}
