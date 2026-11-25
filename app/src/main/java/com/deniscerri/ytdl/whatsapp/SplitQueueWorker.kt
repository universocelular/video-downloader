package com.deniscerri.ytdl.whatsapp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Works through the [SplitQueue] one job at a time, as a foreground service with a notification, so a split goes on
 * when the user leaves the app (diseño/imagenes/06-3-procesando.png). [SplitQueue.startWorker] starts it.
 */
class SplitQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val queue = SplitQueue.get(context)
    private val notifications = SplitNotifications(context)

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.foregroundInfo(null, null, waiting())

    override suspend fun doWork(): Result {
        queue.requeueInterrupted()
        // Android can refuse to start a foreground service from the background; the work then still runs, less protected
        runCatching { setForeground(notifications.foregroundInfo(null, null, waiting())) }
        while (true) {
            val job = queue.takeNext() ?: break
            run(job)
        }
        return Result.success()
    }

    // A supervisor scope, so a failed split reaches the catch below instead of failing the whole worker
    private suspend fun run(job: SplitJob) = supervisorScope {
        var shownAt = 0L
        var processStartedAt = 0L
        val work = async(Dispatchers.IO) {
            SplitRunner(applicationContext).run(job) { step, fraction ->
                val now = SystemClock.elapsedRealtime()
                if (step == SplitStep.PROCESS && processStartedAt == 0L) processStartedAt = now
                val remaining = if (step == SplitStep.PROCESS && fraction != null) SplitEta.remainingMs(now - processStartedAt, fraction) else null
                val progress = SplitProgress(step, fraction, remaining)
                queue.report(job.id, progress)
                if (now - shownAt >= NOTIFICATION_INTERVAL_MS || step == SplitStep.SAVE) {
                    shownAt = now
                    notifications.showProgress(job, progress, waiting())
                }
            }
        }
        queue.attach(job.id, work)
        notifications.showProgress(job, null, waiting())
        val parts = try {
            work.await()
        } catch (e: CancellationException) {
            // The system stopping the worker leaves the job and its folder to start over next time
            if (!isActive) throw e
            queue.finish(job.id, SplitJobStatus.CANCELLED)
            File(job.jobDir).deleteRecursively()
            return@supervisorScope
        } catch (e: Exception) {
            val message = failureMessage(e)
            queue.finish(job.id, SplitJobStatus.FAILED, error = message)
            runCatching { notifications.failed(job, message) }
            File(job.jobDir).deleteRecursively()
            return@supervisorScope
        }
        queue.finish(job.id, SplitJobStatus.DONE, parts)
        queue.forgetReplaced(job.replaceUris)
        runCatching { notifications.done(job, parts) }
        File(job.jobDir).deleteRecursively()
    }

    private fun failureMessage(error: Exception): String {
        val detail = error.message.orEmpty().lines().lastOrNull { it.isNotBlank() }.orEmpty().trim().take(300)
        return (applicationContext.getString(R.string.vd_split_error_cut) + "\n\n" + detail).trim()
    }

    private fun waiting() = queue.jobs.value.count { it.status == SplitJobStatus.QUEUED }

    companion object {
        private const val NOTIFICATION_INTERVAL_MS = 1_000L
    }
}

/** Makes and saves the parts of one job: the work behind "Dividir" */
class SplitRunner(private val context: Context) {
    private val splitter = WhatsAppSplitter(context)

    suspend fun run(job: SplitJob, onProgress: (SplitStep, Float?) -> Unit): List<SavedSplitPart> {
        val source = File(job.sourcePath)
        val probe = VideoProbe.read(source)
        val plan = SplitPlanner.forSettings(probe, job.seconds, job.mode, job.exact, job.rangeStartUs, job.rangeEndUs)
            ?: throw IOException("These settings make no parts of ${source.name}")
        onProgress(SplitStep.PROCESS, if (job.exact) 0f else null)
        val outDir = File(job.jobDir, "parts")
        val files = if (job.exact) {
            ExactSplitter(context).cut(source, plan, probe, outDir) { index, fraction -> onProgress(SplitStep.PROCESS, (index + fraction) / plan.size) }
        } else {
            splitter.cut(source, plan, probe.durationUs, outDir)
        }
        onProgress(SplitStep.SAVE, null)
        val ready = if (job.exact) splitter.rewriteForStatus(files) else files
        // a new split of the same video replaces the parts the previous one left in the gallery
        job.replaceUris.forEach { splitter.deleteSaved(SavedPart(Uri.parse(it), null)) }
        val saved = splitter.saveToGallery(ready, job.title)
        val maxUs = job.seconds * 1_000_000L
        return ready.mapIndexed { index, part ->
            // Exact parts are cut where planned, and the encoded file can report a fraction of a second less
            val durationUs = if (job.exact && index < plan.size) plan[index].durationUs else VideoProbe.durationUs(part)
            SavedSplitPart(saved[index].uri.toString(), durationUs, durationUs > maxUs + OVER_LIMIT_TOLERANCE_US)
        }
    }

    companion object {
        // Container durations round a little; a part a quarter second over is still accepted in full
        private const val OVER_LIMIT_TOLERANCE_US = 250_000L
    }
}

/** The name of a step, on the working screen and in the notification */
internal fun stepLabel(resources: Resources, step: SplitStep, exact: Boolean, fromLink: Boolean): String = resources.getString(
    when (step) {
        SplitStep.OBTAIN -> if (fromLink) R.string.vd_split_downloading else R.string.vd_split_step_open
        SplitStep.PROCESS -> if (exact) R.string.vd_split_step_prepare else R.string.vd_split_step_cut
        SplitStep.SAVE -> R.string.vd_split_step_save
    }
)

/** "Preparando para WhatsApp · 64 % · faltan 1:20", or just the step while how far it is is unknown */
internal fun stepText(resources: Resources, label: String, progress: SplitProgress?): String {
    val fraction = progress?.fraction ?: return label
    val percent = (fraction * 100).roundToInt()
    val remaining = progress.remainingMs ?: return resources.getString(R.string.vd_split_step_percent, label, percent)
    return resources.getString(R.string.vd_split_step_percent_left, label, percent, SplitClock.format(remaining * 1000))
}

/** The notifications of "Dividir para estados": the one that keeps the work going, and the one that says it ended */
class SplitNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    fun foregroundInfo(job: SplitJob?, progress: SplitProgress?, waiting: Int): ForegroundInfo {
        val notification = progressNotification(job, progress, waiting)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(FOREGROUND_ID, notification)
        }
    }

    fun showProgress(job: SplitJob, progress: SplitProgress?, waiting: Int) = notify(FOREGROUND_ID, progressNotification(job, progress, waiting))

    /** "¡Listo! 5 partes", with a button that shares them right from the notification */
    fun done(job: SplitJob, parts: List<SavedSplitPart>) {
        val builder = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setContentTitle(context.resources.getQuantityString(R.plurals.vd_split_done, parts.size, parts.size))
            .setContentText(job.title)
            .setAutoCancel(true)
            .setContentIntent(openJob(job.id))
        if (parts.isNotEmpty()) {
            runCatching {
                val share = WhatsAppSplitter(context).shareIntent(parts.map { Uri.parse(it.uri) }, context.getString(R.string.vd_split_share))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                builder.addAction(0, context.getString(R.string.vd_split_share), PendingIntent.getActivity(context, requestCode(job.id, SHARE), share, PENDING_FLAGS))
            }
        }
        notify(notificationId(job.id), builder.build())
    }

    fun failed(job: SplitJob, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setContentTitle(context.getString(R.string.vd_split_notif_failed, job.title))
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openJob(job.id))
            .build()
        notify(notificationId(job.id), notification)
    }

    /** The screen shows the finished job, so its notification has said what it had to */
    fun dismiss(jobId: String) {
        runCatching { manager.cancel(notificationId(jobId)) }
    }

    private fun progressNotification(job: SplitJob?, progress: SplitProgress?, waiting: Int): Notification {
        val resources = context.resources
        val title = job?.let { context.getString(R.string.vd_split_notif_working, it.title) } ?: context.getString(R.string.vd_split_screen_title)
        val text = listOfNotNull(
            job?.let { stepText(resources, stepLabel(resources, progress?.step ?: SplitStep.PROCESS, it.exact, fromLink = false), progress) },
            waiting.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.vd_split_notif_waiting, it, it) },
        ).joinToString(" · ")
        val fraction = progress?.fraction
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).roundToInt(), fraction == null)
            .setContentIntent(openJob(job?.id))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun openJob(id: String?): PendingIntent {
        val intent = Intent(context, SplitForWhatsAppActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (id != null) intent.putExtra(SplitForWhatsAppActivity.EXTRA_JOB_ID, id)
        return PendingIntent.getActivity(context, requestCode(id, OPEN), intent, PENDING_FLAGS)
    }

    // Without permission to notify, Android shows nothing and the work goes on all the same
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        if (manager.areNotificationsEnabled()) runCatching { manager.notify(id, notification) }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.vd_split_channel_progress), NotificationManager.IMPORTANCE_LOW)
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.vd_split_channel_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun notificationId(jobId: String) = FINISHED_ID_BASE + (jobId.hashCode() and 0xFFFFF)

    private fun requestCode(jobId: String?, kind: Int) = (jobId?.hashCode() ?: 0) * 4 + kind

    companion object {
        private const val CHANNEL_PROGRESS = "vd_split_progress"
        private const val CHANNEL_DONE = "vd_split_done"
        // far from the ids the downloads use, which come from their database rows
        private const val FOREGROUND_ID = 731_000_000
        private const val FINISHED_ID_BASE = 732_000_000
        private const val PROGRESS_MAX = 100
        private const val OPEN = 0
        private const val SHARE = 1
        private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
