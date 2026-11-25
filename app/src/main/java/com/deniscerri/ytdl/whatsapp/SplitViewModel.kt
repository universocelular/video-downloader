package com.deniscerri.ytdl.whatsapp

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.ui.downloads.DownloadErrorReason
import com.deniscerri.ytdl.ui.downloads.FileKind
import com.deniscerri.ytdl.ui.downloads.historyEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Where "Dividir para estados" is: choosing a video, getting it, choosing how to split it, following a job of the
 * [SplitQueue], or failed getting the video ready
 */
enum class SplitStage { CHOOSE, OBTAINING, OPTIONS, JOB, ERROR }

/** The steps the working screen lists (diseño/imagenes/06-3-procesando.png) */
enum class SplitStep { OBTAIN, PROCESS, SAVE }

/** A video the app downloaded lately, offered under "Recientes" (diseño/imagenes/06-1-elegir.png) */
data class RecentVideo(val key: String, val title: String, val item: StatusItem, val path: String?)

data class SplitUiState(
    val stage: SplitStage = SplitStage.CHOOSE,
    /** The video comes from a link, so getting it means downloading it */
    val fromLink: Boolean = false,
    val link: String = "",
    val title: String = "",
    /** How far getting the video is, from 0 to 1, or null while that is unknown */
    val obtainProgress: Float? = null,
    val seconds: Int = SplitViewModel.DEFAULT_SECONDS,
    val mode: SplitMode = SplitMode.WHOLE,
    /** Exact lengths by re-encoding (true) or the original quality cut on keyframes (false) */
    val exact: Boolean = true,
    val rangeStartUs: Long = 0,
    val rangeEndUs: Long = 0,
    val durationUs: Long = 0,
    /** Small frames spread along the video: its card, the strip of "Un tramo" and the working animation */
    val frames: List<Bitmap> = emptyList(),
    /** The parts the current settings would make; empty when they cannot make any, as with a range too short */
    val plan: List<SplitPart> = emptyList(),
    val recents: List<RecentVideo> = emptyList(),
    val errorMessage: String? = null,
    /** The job on screen in [SplitStage.JOB] */
    val jobId: String? = null,
    /** Jobs made from the video now loaded, whose working screen can show its frames and parts */
    val draftJobs: List<String> = emptyList(),
)

/**
 * Gets a video ready on the screen (download, copy or a recent download), keeps the chosen settings, and hands each
 * "Dividir" to the [SplitQueue], which splits it in the background
 */
class SplitViewModel(application: Application) : AndroidViewModel(application) {
    private val splitter = WhatsAppSplitter(application)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val notifications = SplitNotifications(application)
    val queue = SplitQueue.get(application)

    private val _state = MutableStateFlow(
        SplitUiState(
            seconds = prefs.getInt(KEY_SECONDS, DEFAULT_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS),
            mode = SplitMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: SplitMode.WHOLE,
            exact = prefs.getBoolean(KEY_EXACT, true),
        )
    )
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    private var obtainJob: Job? = null
    // The video on screen ("draft"): its folder, the file and what was read from it
    private var draftDir: File? = null
    private var source: File? = null
    private var probe: VideoProbe? = null

    init {
        viewModelScope.launch {
            val recents = withContext(Dispatchers.IO) { loadRecents() }
            _state.update { it.copy(recents = recents) }
        }
        viewModelScope.launch(Dispatchers.IO) { splitter.cleanLeftovers(queue.activeDirs()) }
        queue.startWorker()
        viewModelScope.launch {
            queue.jobs.collect { jobs ->
                val current = state.value
                if (current.stage != SplitStage.JOB) return@collect
                val job = jobs.firstOrNull { it.id == current.jobId }
                // a cancelled job leaves the list, and the screen goes back with it
                if (job == null) leaveJob() else if (!job.active) notifications.dismiss(job.id)
            }
        }
    }

    fun startWithUrl(url: String) = obtain(fromLink = true, link = url) { dir ->
        splitter.download(url, dir) { percent ->
            _state.update { it.copy(obtainProgress = (percent / 100f).coerceIn(0f, 1f)) }
        }
    }

    fun startWithVideo(uri: Uri) = obtain(fromLink = false, link = "") { dir ->
        withContext(Dispatchers.IO) { splitter.copyShared(uri, dir) }
    }

    /** A recent download is read where it is; it is copied only when the app cannot open the file directly */
    fun startWithRecent(recent: RecentVideo) {
        val file = recent.path?.let(::File)?.takeIf { it.canRead() }
        if (file != null) obtain(fromLink = false, link = "") { file } else startWithVideo(recent.item.uri)
    }

    fun setSeconds(seconds: Int) {
        val value = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        prefs.edit { putInt(KEY_SECONDS, value) }
        _state.update { it.copy(seconds = value) }
        refreshPlan()
    }

    fun setMode(mode: SplitMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _state.update { it.copy(mode = mode) }
        refreshPlan()
    }

    fun setExact(exact: Boolean) {
        prefs.edit { putBoolean(KEY_EXACT, exact) }
        _state.update { it.copy(exact = exact) }
        refreshPlan()
    }

    fun setRange(startUs: Long, endUs: Long) {
        _state.update { it.copy(rangeStartUs = startUs, rangeEndUs = endUs) }
        refreshPlan()
    }

    /** "Dividir": hands the video and the settings to the queue, which splits it even after the user leaves */
    fun split() {
        val file = source ?: return
        val draft = draftDir ?: return
        val current = state.value
        if (current.plan.isEmpty()) return
        // a new split of the same video replaces the parts its previous split saved
        val previous = current.draftJobs.asReversed().firstNotNullOfOrNull { id ->
            queue.jobs.value.firstOrNull { it.id == id && it.status == SplitJobStatus.DONE }
        }
        viewModelScope.launch {
            runCatchingFailures {
                val job = withContext(Dispatchers.IO) {
                    val id = UUID.randomUUID().toString()
                    val dir = splitter.newJobDir(id)
                    // the job keeps its own name for a downloaded or copied video; a recent download is read where it is
                    val video = if (file.absolutePath.startsWith(draft.absolutePath + File.separator)) splitter.shareWithJob(file, dir) else file
                    SplitJob(
                        id = id,
                        title = current.title,
                        createdAt = System.currentTimeMillis(),
                        sourcePath = video.absolutePath,
                        jobDir = dir.absolutePath,
                        seconds = current.seconds,
                        mode = current.mode,
                        exact = current.exact,
                        rangeStartUs = current.rangeStartUs,
                        rangeEndUs = current.rangeEndUs,
                        replaceUris = previous?.parts?.map { it.uri }.orEmpty(),
                    )
                }
                queue.enqueue(job)
                _state.update { it.copy(stage = SplitStage.JOB, jobId = job.id, draftJobs = it.draftJobs + job.id) }
            }
        }
    }

    /** A job from "En proceso" or from its notification */
    fun openJob(id: String) {
        val job = queue.jobs.value.firstOrNull { it.id == id } ?: return
        if (!job.active) notifications.dismiss(id)
        _state.update { it.copy(stage = SplitStage.JOB, jobId = id) }
    }

    /** "Cancelar" on a job; once the queue lets it go, the screen goes back */
    fun cancelJob() {
        state.value.jobId?.let(queue::cancel)
    }

    /** One step back, as the arrow and Back do; false when the screen should close */
    fun back(): Boolean {
        when (state.value.stage) {
            SplitStage.CHOOSE -> return false
            SplitStage.OBTAINING, SplitStage.OPTIONS -> toChoose()
            SplitStage.JOB -> leaveJob()
            SplitStage.ERROR -> if (source != null) _state.update { it.copy(stage = SplitStage.OPTIONS, errorMessage = null) } else toChoose()
        }
        return true
    }

    /** After a failure getting the video ready: its options when it is still at hand, otherwise download it again */
    fun retry() {
        val link = state.value.link
        when {
            source != null -> _state.update { it.copy(stage = SplitStage.OPTIONS, errorMessage = null) }
            link.isNotBlank() -> startWithUrl(link)
            else -> toChoose()
        }
    }

    /** "Dividir otro video"; jobs already in the queue carry on */
    fun toChoose() {
        obtainJob?.cancel()
        dropDraft()
        _state.update {
            it.copy(
                stage = SplitStage.CHOOSE, link = "", title = "", obtainProgress = null, frames = emptyList(), plan = emptyList(),
                errorMessage = null, jobId = null, draftJobs = emptyList(),
            )
        }
    }

    override fun onCleared() {
        obtainJob?.cancel()
        // only the video on screen goes; queued jobs have their own copies and carry on
        draftDir?.deleteRecursively()
    }

    /** From a job back to the options of the video on screen, or to choosing one; the job goes on in the background */
    private fun leaveJob() {
        _state.update { it.copy(stage = if (source != null) SplitStage.OPTIONS else SplitStage.CHOOSE, jobId = null) }
    }

    private fun dropDraft() {
        source = null
        probe = null
        draftDir?.deleteRecursively()
        draftDir = null
    }

    private fun obtain(fromLink: Boolean, link: String, get: suspend (File) -> File) {
        obtainJob?.cancel()
        dropDraft()
        val dir = splitter.newDraftDir()
        draftDir = dir
        _state.update {
            it.copy(
                stage = SplitStage.OBTAINING, fromLink = fromLink, link = link, title = "", obtainProgress = if (fromLink) 0f else null,
                frames = emptyList(), plan = emptyList(), errorMessage = null, jobId = null, draftJobs = emptyList(),
            )
        }
        obtainJob = viewModelScope.launch {
            runCatchingFailures {
                val file = get(dir)
                val read = withContext(Dispatchers.IO) { VideoProbe.read(file) }
                val frames = withContext(Dispatchers.IO) {
                    framesAt(file, (0 until FRAME_COUNT).map { read.durationUs * (2 * it + 1) / (2 * FRAME_COUNT) }).filterNotNull()
                }
                source = file
                probe = read
                _state.update {
                    it.copy(
                        stage = SplitStage.OPTIONS, title = WhatsAppSplitter.cleanTitle(file.name), durationUs = read.durationUs,
                        rangeStartUs = 0, rangeEndUs = read.durationUs, frames = frames,
                    )
                }
                refreshPlan()
            }
        }
    }

    private fun refreshPlan() = _state.update {
        val read = probe
        val plan = if (read == null) null else SplitPlanner.forSettings(read, it.seconds, it.mode, it.exact, it.rangeStartUs, it.rangeEndUs)
        it.copy(plan = plan.orEmpty())
    }

    private fun loadRecents(): List<RecentVideo> = runCatching {
        val app = getApplication<Application>()
        DBManager.getInstance(app).historyDao.getRecentVideos(RECENT_LOOKUP)
            // one file the app cannot share (outside the FileProvider folders) must not hide the others
            .mapNotNull { runCatching { historyEntry(app, it) }.getOrNull() }
            .mapNotNull { entry -> entry.local?.takeIf { entry.kind == FileKind.Video }?.let { RecentVideo(entry.key, entry.title, it, entry.path) } }
            .take(RECENT_COUNT)
    }.getOrDefault(emptyList())

    /** Small pictures of [file] at [timesUs], null where a frame cannot be read */
    private fun framesAt(file: File, timesUs: List<Long>): List<Bitmap?> {
        if (timesUs.isEmpty()) return emptyList()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            timesUs.map { time ->
                runCatching { retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let(::shrink) }.getOrNull()
            }
        } catch (e: Exception) {
            timesUs.map { null }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun shrink(frame: Bitmap): Bitmap {
        val scale = FRAME_SIDE_PX.toFloat() / maxOf(frame.width, frame.height)
        if (scale >= 1f) return frame
        val width = (frame.width * scale).roundToInt().coerceAtLeast(1)
        val height = (frame.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(frame, width, height, true).also { if (it !== frame) frame.recycle() }
    }

    private suspend fun runCatchingFailures(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun fail(error: Exception) {
        val detail = error.message.orEmpty().lines().lastOrNull { it.isNotBlank() }.orEmpty().trim().take(300)
        val needsAccount = listOf("sign in", "login", "log in", "cookies").any { detail.contains(it, ignoreCase = true) }
        val current = state.value
        val message = when {
            needsAccount -> text(R.string.vd_login_not_supported)
            current.stage == SplitStage.OBTAINING && current.fromLink -> {
                // the same plain words as Archivos › En curso › Con error, and the raw answer when there are none
                val reason = DownloadErrorReason.of(error.message)
                if (reason == DownloadErrorReason.Unknown) text(R.string.vd_split_error_download) + "\n\n" + detail else text(reason.message)
            }
            current.stage == SplitStage.OBTAINING -> text(R.string.vd_split_error_open) + "\n\n" + detail
            else -> text(R.string.vd_split_error_cut) + "\n\n" + detail
        }
        _state.update { it.copy(stage = SplitStage.ERROR, errorMessage = message.trim()) }
    }

    private fun text(resource: Int) = getApplication<Application>().getString(resource)

    companion object {
        const val DEFAULT_SECONDS = 90
        const val MIN_SECONDS = 5
        const val MAX_SECONDS = 600
        val PRESET_SECONDS = listOf(30, 60, 90)
        private const val KEY_SECONDS = "vd_split_seconds"
        private const val KEY_MODE = "vd_split_mode"
        private const val KEY_EXACT = "vd_split_exact"
        private const val RECENT_COUNT = 6
        // history rows whose file is gone are skipped, so a few more are read than shown
        private const val RECENT_LOOKUP = 40
        private const val FRAME_COUNT = 6
        private const val FRAME_SIDE_PX = 320
    }
}
