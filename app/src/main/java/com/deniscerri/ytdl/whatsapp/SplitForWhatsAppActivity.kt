package com.deniscerri.ytdl.whatsapp

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.player.VideoPlayerActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * "Dividir para estados": receives a link or a video from the share menu (or a pasted link, a video from the
 * gallery or a recent download), lets the user choose how to split it and hands it to the [SplitQueue], which
 * splits it in the background and tells when the parts are ready to go to WhatsApp.
 */
class SplitForWhatsAppActivity : BaseActivity() {
    private lateinit var viewModel: SplitViewModel

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.startWithVideo(uri)
    }

    // The split goes on whatever the answer; without the permission it only cannot say when it is done
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SplitViewModel::class.java]
        if (savedInstanceState == null) handleShare(intent)

        // Back goes one step back, as the arrow does: the options, then choosing another video, then out
        onBackPressedDispatcher.addCallback(this) {
            if (!viewModel.back()) finish()
        }

        // the screen stays on while the video downloads here; the split itself goes on in the background
        lifecycleScope.launch {
            viewModel.state.map { it.stage == SplitStage.OBTAINING }.distinctUntilChanged().collect { busy ->
                if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    val state by viewModel.state.collectAsState()
                    val jobs by viewModel.queue.jobs.collectAsState()
                    val progress by viewModel.queue.progress.collectAsState()
                    SplitScreen(
                        state,
                        jobs,
                        progress,
                        SplitActions(
                            onPaste = { pasteLink() },
                            onPickVideo = { pickVideo.launch("video/*") },
                            onRecent = viewModel::startWithRecent,
                            onOpenJob = viewModel::openJob,
                            onSeconds = viewModel::setSeconds,
                            onMode = viewModel::setMode,
                            onExact = viewModel::setExact,
                            onRange = viewModel::setRange,
                            onSplit = {
                                askForNotifications()
                                viewModel.split()
                            },
                            onCancelJob = viewModel::cancelJob,
                            onShare = ::share,
                            onOpenPart = ::open,
                            onPlayPart = { startActivity(VideoPlayerActivity.intent(this@SplitForWhatsAppActivity, it)) },
                            onAnother = viewModel::toChoose,
                            onRetry = viewModel::retry,
                            onBack = { if (!viewModel.back()) finish() },
                        ),
                    )
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        // a notification of a split opens its job
        intent?.getStringExtra(EXTRA_JOB_ID)?.let {
            viewModel.openJob(it)
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val video = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (video != null && intent.type.orEmpty().startsWith("video/")) {
            viewModel.startWithVideo(video)
            return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        findUrl(text)?.let(viewModel::startWithUrl)
    }

    /** Android 13 and later ask before an app may notify; the first "Dividir" is when the notifications start to matter */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Takes the copied link and starts right away, so copying a link is as quick as sharing it */
    private fun pasteLink() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val url = findUrl(text)
        if (url != null) viewModel.startWithUrl(url) else Toast.makeText(this, R.string.vd_split_clipboard_empty, Toast.LENGTH_SHORT).show()
    }

    private fun share(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startActivity(WhatsAppSplitter(this).shareIntent(uris, getString(R.string.vd_split_share)))
    }

    /** A part in the phone's video player, where the gallery's other parts are a swipe away */
    private fun open(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.vd_files_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun findUrl(text: String): String? =
        URL_PATTERN.find(text)?.value?.trimEnd('.', ',', ')', ']', '»', '"')

    companion object {
        /** The job a split's notification opens */
        const val EXTRA_JOB_ID = "vd_split_job"
        private val URL_PATTERN = Regex("""https?://\S+""")
    }
}
