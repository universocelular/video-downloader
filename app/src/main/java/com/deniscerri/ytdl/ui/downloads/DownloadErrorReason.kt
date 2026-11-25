package com.deniscerri.ytdl.ui.downloads

import androidx.annotation.StringRes
import com.deniscerri.ytdl.R

/**
 * Why a download failed, in words anyone understands (Archivos › En curso › Con error, diseño/imagenes/08-3-con-error.png).
 * It is read from the end of the download's log, where the worker writes yt-dlp's error.
 */
enum class DownloadErrorReason(@StringRes val message: Int) {
    Storage(R.string.vd_error_storage),
    Folder(R.string.vd_error_folder),
    Unsupported(R.string.vd_error_unsupported),
    NotStarted(R.string.vd_error_not_started),
    Geo(R.string.vd_error_geo),
    SignIn(R.string.vd_error_sign_in),
    RateLimit(R.string.vd_error_rate_limit),
    Format(R.string.vd_error_format),
    Processing(R.string.vd_error_processing),
    Unavailable(R.string.vd_error_unavailable),
    Forbidden(R.string.vd_error_forbidden),
    Network(R.string.vd_error_network),
    Unknown(R.string.vd_error_unknown);

    companion object {
        // The first match wins, so specific causes come before generic ones: Instagram's "rate-limit reached or
        // login required" is about signing in, "requested format is not available" is not a missing video,
        // and a 404 page is not a network cut.
        private val patterns: List<Pair<DownloadErrorReason, List<String>>> = listOf(
            Storage to listOf("no space left", "enospc", "disk full"),
            Folder to listOf("permission denied", "read-only file system", "operation not permitted"),
            Unsupported to listOf("unsupported url", "is not a valid url"),
            NotStarted to listOf("premieres in", "live event will begin", "is not yet live", "will begin in a few moments"),
            Geo to listOf("in your country", "geo restrict", "geo-restrict", "georestrict"),
            SignIn to listOf(
                "sign in", "log in", "login", "logged in", "cookies", "private video", "members-only", "members only",
                "join this channel", "confirm your age", "age-restricted", "age restricted", "inappropriate for some users",
                "registered users", "premium members", "http error 401",
            ),
            RateLimit to listOf("http error 429", "too many requests", "rate-limit", "rate limit"),
            Format to listOf("requested format is not available", "format is not available"),
            Processing to listOf("postprocessing", "conversion failed", "ffmpeg", "ffprobe"),
            Unavailable to listOf(
                "video unavailable", "is unavailable", "has been removed", "no longer available", "not available",
                "does not exist", "http error 404", "http error 410", "not found",
            ),
            Forbidden to listOf("http error 403", "forbidden"),
            Network to listOf(
                "unable to download webpage", "timed out", "timeout", "connection reset", "connection refused",
                "connection aborted", "network is unreachable", "no address associated", "name resolution",
                "failed to resolve", "getaddrinfo", "remote end closed", "incompleteread", "[ssl", "sslerror",
                "unable to connect", "unknown host", "no route to host",
            ),
        )

        /** [log] is the end of the download's log; null or blank when there is nothing to read. */
        fun of(log: String?): DownloadErrorReason {
            if (log.isNullOrBlank()) return Unknown
            // yt-dlp prints warnings before the error; only the last "ERROR:" says what actually stopped it
            val error = log.substringAfterLast("ERROR:", log).lowercase()
            return patterns.firstOrNull { (_, words) -> words.any { it in error } }?.first ?: Unknown
        }
    }
}
