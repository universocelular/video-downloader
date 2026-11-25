package com.deniscerri.ytdl.ui.downloads

import android.content.res.Resources
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.work.DownloadWorker
import java.util.Locale

/**
 * How a download in progress reads on Inicio and in Archivos › En curso: "Descargando · 72 %", "Uniendo audio y video",
 * "En espera · segundo en la fila". Both screens take it from here, so they always say the same thing.
 */
data class TaskStatus(val text: String, val progress: Float?, val speed: String?)

/**
 * [status] is the download's status in the database, [event] the worker's latest output for it (null until the first
 * one arrives) and [place] its 1-based place among the downloads waiting their turn.
 */
fun taskStatus(resources: Resources, status: String, event: DownloadWorker.WorkerProgress?, place: Int? = null): TaskStatus {
    val running = status == DownloadRepository.Status.Active.toString()
    val paused = status == DownloadRepository.Status.Paused.toString() || status == DownloadRepository.Status.QueuedPaused.toString()
    val text = when {
        paused -> resources.getString(R.string.vd_task_paused)
        !running -> place?.let { resources.getString(R.string.vd_task_waiting_place, ordinal(resources, it)) }
            ?: resources.getString(R.string.vd_task_waiting)
        event == null -> resources.getString(R.string.vd_task_starting)
        event.output.contains("[Merger]") || event.output.contains("Merging formats") -> resources.getString(R.string.vd_task_merging)
        event.output.contains("[ExtractAudio]") -> resources.getString(R.string.vd_task_converting)
        else -> resources.getString(R.string.vd_task_downloading, event.progress)
    }
    val progress = event?.progress?.takeIf { running && it in 1..100 }?.let { it / 100f }
    val speed = event?.takeIf { running }
        ?.let { DownloadSpeed.parse(it.output, resources.configuration.locales[0] ?: Locale.getDefault()) }
        ?.let { resources.getString(R.string.vd_task_speed, it.speed, it.timeLeft) }
    return TaskStatus(text, progress, speed)
}

/** "primero", "segundo"… and "11.º" past the words the language has. */
internal fun ordinal(resources: Resources, place: Int): String =
    resources.getStringArray(R.array.vd_ordinals).getOrNull(place - 1) ?: resources.getString(R.string.vd_ordinal_number, place)

/** Speed and time left, read from a yt-dlp progress line such as "[download]  72.0% of 120.53MiB at 3.20MiB/s ETA 00:12". */
data class DownloadSpeed(val speed: String, val timeLeft: String) {
    companion object {
        private val speedPattern = Regex("""\bat\s+(\d+(?:\.\d+)?)\s*([KMGT]?)i?B/s""")
        private val etaPattern = Regex("""\bETA\s+(?:(\d+):)?(\d{1,2}):(\d{2})\b""")

        /** Null when the line has no speed or no time left, as when yt-dlp still says "Unknown". */
        fun parse(output: String, locale: Locale): DownloadSpeed? {
            val speed = speedPattern.find(output) ?: return null
            val eta = etaPattern.find(output) ?: return null
            val value = speed.groupValues[1].toDouble()
            val number = String.format(locale, if (value < 10) "%.1f" else "%.0f", value)
            val hours = eta.groupValues[1].toIntOrNull() ?: 0
            val minutes = eta.groupValues[2].toInt()
            val seconds = eta.groupValues[3].toInt()
            val left = when {
                hours > 0 -> "$hours h $minutes min"
                minutes > 0 -> "${if (seconds >= 30) minutes + 1 else minutes} min"
                else -> "$seconds s"
            }
            return DownloadSpeed("$number ${speed.groupValues[2]}B/s", left)
        }
    }
}
