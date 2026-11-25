package com.deniscerri.ytdl.whatsapp

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import java.io.File
import java.io.IOException
import java.util.Locale

/** A part saved where the user can find it. [file] is set when it lives in a plain folder instead of MediaStore */
data class SavedPart(val uri: Uri, val file: File?)

/** True when the app [packageName] is on the phone; the manifest lists the WhatsApp apps so they can be seen */
internal fun Context.isAppInstalled(packageName: String): Boolean = try {
    packageManager.getPackageInfo(packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

/**
 * Gets a video from a link or a share, writes its WhatsApp status parts as plain MP4 files with FFmpeg,
 * saves the parts to the gallery and opens the share sheet that hands them to WhatsApp or another app.
 */
class WhatsAppSplitter(private val context: Context) {
    private val workRoot = File(context.cacheDir, "whatsapp_split")

    /** A fresh folder for the video being prepared on the screen; it stays until the screen lets the video go */
    fun newDraftDir(): File = File(workRoot, DRAFT_PREFIX + System.currentTimeMillis()).apply { mkdirs() }

    /** The folder a queued job keeps its video and parts in; the job removes it when it ends */
    fun newJobDir(id: String): File = File(workRoot, JOB_PREFIX + id).apply { mkdirs() }

    /**
     * Gives a job its own name for [source], so the screen can let go of its copy while the job still needs it.
     * A second name for the same file costs no space; where the phone does not allow one, the video is copied.
     */
    fun shareWithJob(source: File, jobDir: File): File {
        val target = File(jobDir, source.name)
        try {
            Os.link(source.absolutePath, target.absolutePath)
        } catch (e: ErrnoException) {
            source.copyTo(target, overwrite = true)
        }
        return target
    }

    /** Removes what an app closed in the middle left behind: folders of jobs that ended, and old drafts */
    fun cleanLeftovers(keep: Set<String>) {
        val now = System.currentTimeMillis()
        workRoot.listFiles()?.forEach { dir ->
            val inUse = when {
                dir.absolutePath in keep -> true
                dir.name.startsWith(DRAFT_PREFIX) -> now - dir.lastModified() < DRAFT_KEEP_MS
                // a job being handed to the queue right now, from another Dividir screen
                dir.name.startsWith(JOB_PREFIX) -> now - dir.lastModified() < NEW_JOB_GRACE_MS
                else -> false
            }
            if (!inUse) dir.deleteRecursively()
        }
    }

    /**
     * Downloads [url] into [dir], preferring what WhatsApp takes without converting: H.264 up to 1080p and 30 fps,
     * with stereo AAC (without the stereo preference yt-dlp picks 5.1 audio when a site offers it)
     */
    suspend fun download(url: String, dir: File, onProgress: (Float) -> Unit): File {
        val request = YTDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--newline")
            .addOption("-S", "vcodec:h264,res:1080,fps:30,channels:2,acodec:aac")
            .addOption("--merge-output-format", "mp4")
            .addOption("--remux-video", "mp4")
            .addOption("--no-mtime")
            .addOption("-P", dir.absolutePath)
            .addOption("-o", "%(title).80B.%(ext)s")
        RuntimeManager.runKillable { processId ->
            RuntimeManager.execute(request, processId) { progress, _, _ -> onProgress(progress) }
        }
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS }
            ?.maxByOrNull { it.length() }
            ?: throw IOException("yt-dlp did not produce a video file")
    }

    /** Copies a video shared from another app, because FFmpeg needs a file path */
    fun copyShared(uri: Uri, dir: File): File {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "video.mp4"
        val target = File(dir, name.replace(UNSAFE_NAME_CHARS, "_"))
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    /**
     * Cuts [input] into [parts] in one FFmpeg run with the segment muxer and stream copy, so no frame is re-encoded.
     * Cut times sit slightly before each planned keyframe: the muxer cuts at the first keyframe after the given time,
     * which keeps the plan even if FFmpeg's timeline is a little offset from the one MediaExtractor reported.
     */
    suspend fun cut(input: File, parts: List<SplitPart>, durationUs: Long, outDir: File): List<File> {
        RuntimeManager.assertInit()
        outDir.deleteRecursively()
        outDir.mkdirs()

        val fromUs = parts.first().startUs
        val toUs = parts.last().endUs
        val command = mutableListOf(RuntimeManager.ffmpegLocation.executable.absolutePath, "-hide_banner", "-nostdin", "-y")
        if (fromUs > 0) command += listOf("-ss", seconds(fromUs + SEEK_NUDGE_US))
        if (toUs < durationUs) command += listOf("-t", seconds(toUs - fromUs))
        command += listOf("-i", input.absolutePath) + STREAM_COPY_OPTIONS + listOf(
            "-avoid_negative_ts", "make_zero",
            "-f", "segment",
            "-reset_timestamps", "1",
            "-segment_format", "mp4",
            "-segment_format_options", "movflags=+faststart",
        )
        val cutTimes = parts.drop(1).map { seconds((it.startUs - fromUs - CUT_MARGIN_US).coerceAtLeast(0)) }
        if (cutTimes.isNotEmpty()) command += listOf("-segment_times", cutTimes.joinToString(","))
        command += File(outDir, "part_%03d.mp4").absolutePath

        RuntimeManager.runKillable { processId -> RuntimeManager.executeImpl(command, processId) }
        return outDir.listFiles { file -> file.name.startsWith("part_") && file.length() > 0 }
            ?.sortedBy { it.name }
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException("FFmpeg did not produce any part")
    }

    /**
     * Rewrites each encoded part into the same plain MP4 as [cut] without re-encoding it, as the FFmpeg-made parts
     * that WhatsApp Business accepts are written: index at the start and no extra data. Timestamps stay as encoded,
     * from zero; shifting them as [cut] does would delay the picture by the audio encoder's priming.
     */
    suspend fun rewriteForStatus(files: List<File>): List<File> {
        RuntimeManager.assertInit()
        return files.map { file ->
            val rewritten = File(file.parentFile, "rewritten_${file.name}")
            val command = listOf(RuntimeManager.ffmpegLocation.executable.absolutePath, "-hide_banner", "-nostdin", "-y") +
                listOf("-i", file.absolutePath) + STREAM_COPY_OPTIONS + listOf("-movflags", "+faststart", rewritten.absolutePath)
            RuntimeManager.runKillable { processId -> RuntimeManager.executeImpl(command, processId) }
            if (rewritten.length() == 0L) throw IOException("FFmpeg did not rewrite ${file.name}")
            if (!file.delete() || !rewritten.renameTo(file)) throw IOException("Cannot replace ${file.name}")
            file
        }
    }

    /** Saves the parts in Movies/VideoDownloader, named after the video and numbered in order */
    fun saveToGallery(files: List<File>, title: String): List<SavedPart> = files.mapIndexed { index, file ->
        val name = context.getString(R.string.vd_split_part_name, title, index + 1, files.size) + ".mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) SavedPart(insertIntoMediaStore(file, name), null)
        else saveLegacy(file, name)
    }

    fun deleteSaved(part: SavedPart) {
        runCatching {
            if (part.file != null) part.file.delete() else context.contentResolver.delete(part.uri, null, null)
        }
    }

    /** The phone's share sheet with every part; WhatsApp then asks whether they go to a chat or to the status */
    fun shareIntent(uris: List<Uri>, title: String): Intent {
        grantReadToWhatsApp(uris)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "video/mp4"
        // The clip data carries the read grant for every part on all Android versions
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, title)
    }

    /**
     * Lets WhatsApp and WhatsApp Business read the parts until the phone restarts. The access an intent carries ends
     * when the app closes its share screen, and WhatsApp can still be preparing the status then.
     * If a grant is refused, the intent's access remains.
     */
    private fun grantReadToWhatsApp(uris: List<Uri>) {
        for (app in WhatsAppApp.entries.filter { context.isAppInstalled(it.packageName) }) for (uri in uris) {
            runCatching { context.grantUriPermission(app.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertIntoMediaStore(file: File, name: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$GALLERY_FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        // The same collection the phone's gallery shares from (content://media/external/...)
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("The gallery did not accept $name")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot write $name")
            output.use { target -> file.inputStream().use { it.copyTo(target) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Android 9 and older: public Movies folder when storage access is granted, otherwise the copy in the app cache */
    private fun saveLegacy(file: File, name: String): SavedPart {
        val saved = runCatching {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), GALLERY_FOLDER)
            folder.mkdirs()
            file.copyTo(File(folder, name), overwrite = true)
        }.getOrNull()
        if (saved != null) MediaScannerConnection.scanFile(context, arrayOf(saved.absolutePath), arrayOf("video/mp4"), null)
        // without storage access the part stays in the app's own files, which outlive the job's folder
        val shared = saved ?: file.copyTo(File(File(context.filesDir, PRIVATE_PARTS_FOLDER), name), overwrite = true)
        return SavedPart(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared), saved)
    }

    companion object {
        const val GALLERY_FOLDER = "VideoDownloader"
        private const val PRIVATE_PARTS_FOLDER = "split_parts"
        private const val DRAFT_PREFIX = "draft-"
        private const val JOB_PREFIX = "job-"
        private const val DRAFT_KEEP_MS = 6 * 60 * 60 * 1000L
        private const val NEW_JOB_GRACE_MS = 60 * 1000L
        private const val CUT_MARGIN_US = 200_000L
        private const val SEEK_NUDGE_US = 1_000L
        // Stream copy of the first video and audio tracks, without metadata or chapters
        private val STREAM_COPY_OPTIONS = listOf(
            "-map", "0:v:0", "-map", "0:a:0?",
            "-c", "copy",
            "-map_metadata", "-1", "-map_chapters", "-1",
        )
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp")
        private val UNSAFE_NAME_CHARS = Regex("""[\\/:*?"<>|]""")
        private val NOT_PLAIN_TEXT = Regex("""[^\p{L}\p{N} .,()_-]""")

        private fun seconds(us: Long) = String.format(Locale.ROOT, "%.3f", us / 1_000_000.0)

        /** A short title for file names, with only letters, digits and plain punctuation (no emoji or symbols) */
        fun cleanTitle(fileName: String): String = fileName
            .substringBeforeLast('.')
            .replace(NOT_PLAIN_TEXT, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
            .ifBlank { "video" }
    }
}
