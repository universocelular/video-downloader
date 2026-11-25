package com.deniscerri.ytdl.whatsapp

import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.BaseActivity
import kotlinx.coroutines.launch

/** "Guardar estados": shows the WhatsApp statuses the user has seen, saves the chosen ones to the gallery and deletes the rest */
class SaveStatusesActivity : BaseActivity() {
    private lateinit var viewModel: StatusesViewModel

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.folderPicked(tree) else viewModel.accessCancelled()
    }

    // Gallery copies another install of the app made: Android asks the user before letting this one delete them
    private val deleteRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.galleryConsent(it.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[StatusesViewModel::class.java]
        // Up to Android 9 the copy in the gallery is a plain file, which needs the storage permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) askPermissions(includeOptional = false)

        // Back leaves the choosing first, and closes the full-screen view before the screen
        val stopChoosing = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = viewModel.stopChoosing()
        }
        onBackPressedDispatcher.addCallback(this, stopChoosing)
        val closeViewer = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = viewModel.view(null)
        }
        onBackPressedDispatcher.addCallback(this, closeViewer)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect {
                        closeViewer.isEnabled = it.viewing != null
                        stopChoosing.isEnabled = it.choosing
                    }
                }
                launch { viewModel.notices.collect { Toast.makeText(this@SaveStatusesActivity, it, Toast.LENGTH_LONG).show() } }
                launch {
                    viewModel.requests.collect { request ->
                        when (request) {
                            StatusesRequest.FolderAccess -> pickStatusesFolder()
                            is StatusesRequest.GalleryConsent -> deleteRequest.launch(IntentSenderRequest.Builder(request.sender).build())
                        }
                    }
                }
            }
        }

        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    val state by viewModel.state.collectAsState()
                    StatusesScreen(
                        state,
                        viewModel.thumbnails,
                        StatusesActions(
                            onApp = viewModel::selectApp,
                            onFilter = viewModel::setFilter,
                            onGrantAccess = ::pickStatusesFolder,
                            onView = viewModel::view,
                            onSave = viewModel::save,
                            onStartChoosing = viewModel::startChoosing,
                            onToggle = viewModel::toggle,
                            onChooseAll = viewModel::chooseAll,
                            onStopChoosing = viewModel::stopChoosing,
                            onSaveAll = viewModel::saveAll,
                            onDelete = viewModel::delete,
                            onOpenApp = ::openApp,
                            onClose = { finish() },
                        ),
                    )
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        // Statuses come and go while the user is in WhatsApp
        viewModel.refresh()
    }

    private fun pickStatusesFolder() {
        try {
            pickFolder.launch(viewModel.pickerStart())
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.vd_statuses_no_picker, Toast.LENGTH_LONG).show()
        }
    }

    /** WhatsApp itself, to watch some statuses; coming back refreshes the list (onStart) */
    private fun openApp(app: WhatsAppApp) {
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch != null) startActivity(launch) else Toast.makeText(this, R.string.vd_files_cannot_open, Toast.LENGTH_SHORT).show()
    }
}
