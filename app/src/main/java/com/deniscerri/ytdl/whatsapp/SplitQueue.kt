package com.deniscerri.ytdl.whatsapp

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class SplitJobStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** A part a finished job saved in the gallery */
data class SavedSplitPart(val uri: String, val durationUs: Long, val overLimit: Boolean)

/**
 * One "Dividir" the user asked for: the video, how to cut it and how it went. The queue keeps it in a file,
 * so it outlives the screen and the app (diseño/imagenes/06-3-procesando.png: "Puedes salir de la app")
 */
data class SplitJob(
    val id: String,
    val title: String,
    val createdAt: Long,
    /** The video; inside [jobDir] when the job has its own copy, elsewhere when it is a download read where it is */
    val sourcePath: String,
    /** The job's own folder, removed when the job ends */
    val jobDir: String,
    val seconds: Int,
    val mode: SplitMode,
    val exact: Boolean,
    val rangeStartUs: Long,
    val rangeEndUs: Long,
    /** Parts an earlier split of the same video left in the gallery, replaced by this one's */
    val replaceUris: List<String> = emptyList(),
    val status: SplitJobStatus = SplitJobStatus.QUEUED,
    val parts: List<SavedSplitPart> = emptyList(),
    val error: String? = null,
    val finishedAt: Long = 0,
) {
    val active: Boolean get() = status == SplitJobStatus.QUEUED || status == SplitJobStatus.RUNNING
}

/** How far the running job is; it changes many times a second, so it is not written to the file */
data class SplitProgress(val step: SplitStep, val fraction: Float?, val remainingMs: Long?)

/** The splits waiting, running and done, in order. One instance for the whole app; [SplitQueueWorker] works through it */
class SplitQueue private constructor(private val context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()
    private val _jobs = MutableStateFlow(load())
    val jobs: StateFlow<List<SplitJob>> = _jobs.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, SplitProgress>>(emptyMap())
    val progress: StateFlow<Map<String, SplitProgress>> = _progress.asStateFlow()
    // The coroutine making each running job's parts, so cancelling the job stops that work
    private val working = ConcurrentHashMap<String, Job>()
    private val cancelRequested = ConcurrentHashMap.newKeySet<String>()

    fun enqueue(job: SplitJob) {
        change { it + job }
        startWorker()
    }

    /** Makes sure the worker runs while jobs wait, also after the app was closed in the middle */
    fun startWorker() {
        if (jobs.value.none { it.active }) return
        // appended rather than kept: a worker in its last moment would otherwise miss a job added right then
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SplitQueueWorker>().build())
    }

    /** The next job waiting, now marked running; null when none waits */
    fun takeNext(): SplitJob? = synchronized(lock) {
        val next = _jobs.value.firstOrNull { it.status == SplitJobStatus.QUEUED } ?: return null
        change { list -> list.map { if (it.id == next.id) it.copy(status = SplitJobStatus.RUNNING) else it } }
        next.copy(status = SplitJobStatus.RUNNING)
    }

    /** Jobs left "running" by an app that was closed in the middle start over */
    fun requeueInterrupted() = change { list ->
        list.map { if (it.status == SplitJobStatus.RUNNING && !working.containsKey(it.id)) it.copy(status = SplitJobStatus.QUEUED) else it }
    }

    fun attach(id: String, work: Job) {
        working[id] = work
        if (id in cancelRequested) work.cancel()
    }

    fun report(id: String, progress: SplitProgress) = _progress.update { it + (id to progress) }

    /** Ends a job; a cancelled one leaves the list, a finished one stays to be seen and shared */
    fun finish(id: String, status: SplitJobStatus, parts: List<SavedSplitPart> = emptyList(), error: String? = null) {
        working.remove(id)
        cancelRequested.remove(id)
        _progress.update { it - id }
        change { list ->
            if (status == SplitJobStatus.CANCELLED) list.filterNot { it.id == id }
            else list.map { if (it.id == id) it.copy(status = status, parts = parts, error = error, finishedAt = System.currentTimeMillis()) else it }
        }
    }

    /** One waiting never starts; one running stops making parts, and the worker then finishes it as cancelled */
    fun cancel(id: String) {
        val job = jobs.value.firstOrNull { it.id == id } ?: return
        when (job.status) {
            SplitJobStatus.QUEUED -> {
                finish(id, SplitJobStatus.CANCELLED)
                File(job.jobDir).deleteRecursively()
            }
            SplitJobStatus.RUNNING -> {
                cancelRequested += id
                working[id]?.cancel()
            }
            else -> Unit
        }
    }

    /** Finished jobs whose parts a newer split of the same video replaced leave the list */
    fun forgetReplaced(uris: List<String>) {
        if (uris.isEmpty()) return
        val gone = uris.toSet()
        change { list -> list.filterNot { !it.active && it.parts.isNotEmpty() && it.parts.all { part -> part.uri in gone } } }
    }

    /** The folders of jobs still waiting or running; any other job folder is a leftover */
    fun activeDirs(): Set<String> = jobs.value.filter { it.active }.map { it.jobDir }.toSet()

    private fun change(transform: (List<SplitJob>) -> List<SplitJob>) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val changed = transform(_jobs.value)
        // finished jobs stay a day, and only the latest few, so the list never grows without end
        val kept = changed.filter { !it.active && now - it.finishedAt < KEEP_FINISHED_MS }
            .sortedByDescending { it.finishedAt }
            .take(MAX_FINISHED)
            .map { it.id }
            .toSet()
        val updated = changed.filter { it.active || it.id in kept }
        _jobs.value = updated
        save(updated)
    }

    private fun load(): List<SplitJob> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).mapNotNull { runCatching { jobFrom(array.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun save(jobs: List<SplitJob>) {
        runCatching {
            val array = JSONArray()
            jobs.forEach { array.put(it.toJson()) }
            // written aside and then renamed, so an app closed while saving never leaves half a file
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(array.toString())
            temp.renameTo(file)
        }
    }

    private fun SplitJob.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("createdAt", createdAt)
        .put("sourcePath", sourcePath)
        .put("jobDir", jobDir)
        .put("seconds", seconds)
        .put("mode", mode.name)
        .put("exact", exact)
        .put("rangeStartUs", rangeStartUs)
        .put("rangeEndUs", rangeEndUs)
        .put("replaceUris", JSONArray(replaceUris))
        .put("status", status.name)
        .put("parts", JSONArray().apply {
            parts.forEach { put(JSONObject().put("uri", it.uri).put("durationUs", it.durationUs).put("overLimit", it.overLimit)) }
        })
        .put("error", error)
        .put("finishedAt", finishedAt)

    private fun jobFrom(json: JSONObject): SplitJob {
        val replace = json.optJSONArray("replaceUris")
        val parts = json.optJSONArray("parts")
        return SplitJob(
            id = json.getString("id"),
            title = json.optString("title"),
            createdAt = json.optLong("createdAt"),
            sourcePath = json.getString("sourcePath"),
            jobDir = json.getString("jobDir"),
            seconds = json.optInt("seconds", SplitViewModel.DEFAULT_SECONDS),
            mode = SplitMode.entries.firstOrNull { it.name == json.optString("mode") } ?: SplitMode.WHOLE,
            exact = json.optBoolean("exact", true),
            rangeStartUs = json.optLong("rangeStartUs"),
            rangeEndUs = json.optLong("rangeEndUs"),
            replaceUris = replace?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty(),
            status = SplitJobStatus.valueOf(json.getString("status")),
            parts = parts?.let { array ->
                (0 until array.length()).map { index ->
                    val part = array.getJSONObject(index)
                    SavedSplitPart(part.getString("uri"), part.optLong("durationUs"), part.optBoolean("overLimit"))
                }
            }.orEmpty(),
            error = if (json.has("error")) json.getString("error") else null,
            finishedAt = json.optLong("finishedAt"),
        )
    }

    companion object {
        private const val FILE_NAME = "split_queue.json"
        private const val WORK_NAME = "vd_split_queue"
        private const val KEEP_FINISHED_MS = 24 * 60 * 60 * 1000L
        private const val MAX_FINISHED = 10

        @Volatile
        private var instance: SplitQueue? = null

        fun get(context: Context): SplitQueue =
            instance ?: synchronized(this) { instance ?: SplitQueue(context.applicationContext).also { instance = it } }
    }
}
