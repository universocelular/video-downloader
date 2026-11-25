package com.deniscerri.ytdl.ui.downloads

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import com.deniscerri.ytdl.whatsapp.GlassTheme
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/** Archivos › En curso (QueueScreen.kt): what is downloading, scheduled, failed, cancelled or saved for later. */
class DownloadQueueMainFragment : Fragment(){
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var mainActivity: MainActivity
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var sharedPreferences: SharedPreferences

    private val queue = QueueUiState()
    private var inProgress: List<DownloadItemSimple> = emptyList()
    private val progress = mutableMapOf<Long, DownloadWorker.WorkerProgress>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mainActivity = activity as MainActivity
        notificationUtil = NotificationUtil(mainActivity)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { GlassTheme { QueueScreen(queue, queueActions) } }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        queue.hideThumbnails = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("queue")
        savedInstanceState?.getString(SAVED_TAB)?.let { name -> QueueTab.entries.firstOrNull { it.name == name } }?.let { queue.tab = it }

        downloadViewModel.pausedAllDownloads.observe(viewLifecycleOwner) {
            queue.pauseAll = when (it) {
                DownloadViewModel.PausedAllDownloadsState.PAUSE -> PauseAll.Pause
                DownloadViewModel.PausedAllDownloadsState.RESUME -> PauseAll.Resume
                else -> PauseAll.Hidden
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    WorkerEventBus.events.collect {
                        progress[it.downloadItemID] = it
                        if (queue.tab == QueueTab.Now) showInProgress()
                    }
                }
                // only the chosen list is read, and read again whenever the downloads change
                snapshotFlow { queue.tab }.collectLatest { tab ->
                    when (tab) {
                        QueueTab.Now -> downloadViewModel.inProgressDownloads.collect { items ->
                            inProgress = items
                            progress.keys.retainAll(items.map { it.id }.toSet())
                            showInProgress()
                        }
                        QueueTab.Errors -> downloadViewModel.downloadsWithStatus(DownloadRepository.Status.Error).collect { items ->
                            val logEnds = withContext(Dispatchers.IO) { downloadViewModel.getLogEnds(items.mapNotNull { it.logID }) }
                            queue.list = QueueList(tab, items.map { item ->
                                val reason = DownloadErrorReason.of(item.logID?.let { logEnds[it] })
                                entry(item, QueueKind.Failed, getString(reason.message))
                            })
                        }
                        QueueTab.Scheduled -> downloadViewModel.downloadsWithStatus(DownloadRepository.Status.Scheduled).collect { items ->
                            queue.list = QueueList(tab, items.map { entry(it, QueueKind.Scheduled, startText(it.downloadStartTime)) })
                        }
                        QueueTab.Cancelled -> downloadViewModel.downloadsWithStatus(DownloadRepository.Status.Cancelled).collect { items ->
                            queue.list = QueueList(tab, items.map { entry(it, QueueKind.Cancelled, sourceText(it)) })
                        }
                        QueueTab.Saved -> downloadViewModel.downloadsWithStatus(DownloadRepository.Status.Saved).collect { items ->
                            queue.list = QueueList(tab, items.map { entry(it, QueueKind.Saved, sourceText(it)) })
                        }
                    }
                }
            }
        }

        // from the notification of a failed download: its list, and the download sheet when it asked to fix it
        if (arguments?.getString("tab") != null) {
            queue.tab = QueueTab.Errors
            val reconfigureID = arguments?.getLong("reconfigure") ?: 0L
            if (reconfigureID != 0L) {
                notificationUtil.cancelErroredNotification(reconfigureID.toInt())
                lifecycleScope.launch {
                    kotlin.runCatching {
                        val item = withContext(Dispatchers.IO){
                            downloadViewModel.getItemByID(reconfigureID)
                        }
                        downloadCardViewModel.setResultItem(downloadViewModel.createResultItemFromDownload(item))
                        downloadCardViewModel.setDownloadItem(item)
                        findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                            Pair("type", item.type)
                            )
                        )
                    }
                }
            }
        }

        arguments?.clear()
    }

    private fun showInProgress() {
        var waiting = 0
        queue.list = QueueList(QueueTab.Now, inProgress.map { item ->
            val kind = when (item.status) {
                DownloadRepository.Status.Active.toString() -> QueueKind.Running
                DownloadRepository.Status.Paused.toString(), DownloadRepository.Status.QueuedPaused.toString() -> QueueKind.Paused
                else -> QueueKind.Waiting
            }
            val place = if (kind == QueueKind.Waiting) ++waiting else null
            val status = taskStatus(resources, item.status, progress[item.id], place)
            entry(item, kind, status.text).copy(progress = status.progress, speed = status.speed)
        })
    }

    private fun entry(item: DownloadItemSimple, kind: QueueKind, line: String) = QueueEntry(
        id = item.id,
        kind = kind,
        title = item.title.ifBlank { item.playlistTitle.ifBlank { item.url } },
        thumb = item.thumb,
        duration = item.duration.takeUnless { it.isBlank() || it == "-1" || it.trimStart('0', ':').isEmpty() }.orEmpty(),
        line = line,
    )

    /** "Mañana a las 03:00 · solo con Wi-Fi" (diseño/imagenes/08-4-programados.png). */
    private fun startText(time: Long): String {
        val clock = DateFormat.getTimeFormat(requireContext()).format(Date(time))
        val day = when {
            DateUtils.isToday(time) -> getString(R.string.vd_queue_today_at, clock)
            DateUtils.isToday(time - DateUtils.DAY_IN_MILLIS) -> getString(R.string.vd_queue_tomorrow_at, clock)
            else -> getString(
                R.string.vd_queue_day_at,
                DateUtils.formatDateTime(requireContext(), time, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
                clock,
            )
        }
        return if (sharedPreferences.getBoolean("metered_networks", true)) day else "$day · ${getString(R.string.vd_queue_wifi_only)}"
    }

    /** Who uploaded it, or the site when that is unknown. */
    private fun sourceText(item: DownloadItemSimple) = item.author.ifBlank {
        Uri.parse(item.url).host?.removePrefix("www.") ?: item.url
    }

    private fun confirm(@StringRes message: Int, @StringRes yes: Int, @StringRes no: Int, action: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setNegativeButton(no, null)
            .setPositiveButton(yes) { _, _ -> action() }
            .show()
    }

    private val queueActions = object : QueueActions {
        override fun onReady() {
            findNavController().navigate(R.id.historyFragment, null, navOptions {
                popUpTo(R.id.downloadQueueMainFragment) { inclusive = true }
                launchSingleTop = true
            })
        }

        override fun onGoHome() {
            val bar = activity?.findViewById<NavigationBarView>(R.id.bottomNavigationView)
            if (bar != null) bar.selectedItemId = R.id.homeFragment else findNavController().navigate(R.id.homeFragment)
        }

        override fun onPause(id: Long) {
            lifecycleScope.launch(Dispatchers.IO) { downloadViewModel.pauseDownload(id) }
        }

        override fun onResume(id: Long) {
            downloadViewModel.resumeDownload(id)
        }

        override fun onCancel(id: Long) {
            lifecycleScope.launch(Dispatchers.IO) { downloadViewModel.cancelDownload(id) }
        }

        override fun onPauseAll() {
            lifecycleScope.launch { downloadViewModel.pauseAllDownloads() }
        }

        override fun onResumeAll() {
            downloadViewModel.resumeAllDownloads()
        }

        override fun onCancelAll() = confirm(R.string.vd_queue_cancel_all_confirm, R.string.vd_queue_cancel_all, R.string.vd_queue_keep) {
            downloadViewModel.cancelAllDownloads()
        }

        override fun onReorder(ids: List<Long>) {
            downloadViewModel.reorderQueue(ids)
        }

        override fun onDownloadNow(id: Long) {
            if (queue.tab == QueueTab.Scheduled) {
                lifecycleScope.launch { downloadViewModel.resetScheduleTimeForItemsAndStartDownload(listOf(id)) }
            } else {
                downloadViewModel.reQueueDownloadItems(listOf(id))
            }
        }

        override fun onDownloadAll() {
            lifecycleScope.launch {
                val ids = withContext(Dispatchers.IO) { downloadViewModel.getIDsByStatus(listOf(DownloadRepository.Status.Saved)) }
                downloadViewModel.reQueueDownloadItems(ids)
            }
        }

        override fun onRetry(id: Long) {
            downloadViewModel.reQueueDownloadItems(listOf(id))
        }

        override fun onRetryAll() {
            lifecycleScope.launch {
                val ids = withContext(Dispatchers.IO) { downloadViewModel.getIDsByStatus(listOf(DownloadRepository.Status.Error)) }
                downloadViewModel.reQueueDownloadItems(ids)
            }
        }

        // the log says exactly what yt-dlp answered
        override fun onDetail(id: Long) {
            lifecycleScope.launch {
                val logID = withContext(Dispatchers.IO) { downloadViewModel.getItemByID(id).logID } ?: return@launch
                findNavController().navigate(R.id.downloadLogFragment, bundleOf("logID" to logID))
            }
        }

        override fun onDelete(id: Long) {
            downloadViewModel.deleteDownload(id)
        }

        override fun onDeleteAll() {
            val tab = queue.tab
            confirm(R.string.vd_queue_delete_all_confirm, R.string.vd_queue_delete_all, R.string.cancel) {
                when (tab) {
                    QueueTab.Errors -> downloadViewModel.deleteErrored()
                    QueueTab.Cancelled -> downloadViewModel.deleteCancelled()
                    QueueTab.Saved -> downloadViewModel.deleteSaved()
                    else -> Unit
                }
            }
        }
    }

    /** Tapping Archivos in the bar again goes back to what is downloading now. */
    fun scrollToActive(){
        queue.tab = QueueTab.Now
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_TAB, queue.tab.name)
    }

    companion object {
        private const val SAVED_TAB = "selected_download_tab"
    }
}
