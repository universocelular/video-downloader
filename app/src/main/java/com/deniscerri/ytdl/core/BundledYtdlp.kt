package com.deniscerri.ytdl.core

/**
 * Video Downloader: the APK ships a recent yt-dlp in res/raw/ytdlp. It replaces the installed copy
 * when that copy is missing or older, so installing a newer APK also updates the engine.
 */
object BundledYtdlp {
    /** yt-dlp versions are dates (2026.10.26, or 2026.10.28.233012 for nightly builds), so text order is release order */
    fun shouldInstall(binaryExists: Boolean, installedVersion: String?, bundledVersion: String): Boolean =
        !binaryExists || installedVersion.isNullOrBlank() || installedVersion < bundledVersion
}
