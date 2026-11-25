package com.deniscerri.ytdl.whatsapp

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

enum class StatusFilter { ALL, PHOTOS, VIDEOS }

data class StatusesUiState(
    /** False until the first look at the phone finishes */
    val checked: Boolean = false,
    val loading: Boolean = true,
    /** WhatsApp apps on the phone, and the one whose statuses are shown */
    val apps: List<WhatsAppApp> = emptyList(),
    val app: WhatsAppApp? = null,
    /** False while the user still has to give access to the app's statuses folder */
    val hasAccess: Boolean = false,
    /** False while that access only lets the app read: deleting asks for it again first */
    val canDelete: Boolean = false,
    val items: List<StatusItem> = emptyList(),
    val filter: StatusFilter = StatusFilter.ALL,
    /** Names already in the gallery album, and the ones being copied there now */
    val saved: Set<String> = emptySet(),
    val saving: Set<String> = emptySet(),
    /** Name of the status open in full screen */
    val viewing: String? = null,
    /** Choosing statuses to save or delete several at once (diseño/imagenes/08-7-seleccion.png), and the chosen names */
    val choosing: Boolean = false,
    val chosen: Set<String> = emptySet(),
) {
    val shown: List<StatusItem>
        get() = when (filter) {
            StatusFilter.ALL -> items
            StatusFilter.PHOTOS -> items.filterNot { it.isVideo }
            StatusFilter.VIDEOS -> items.filter { it.isVideo }
        }
}

/** What the screen needs from Android to go on deleting */
sealed interface StatusesRequest {
    /** The folder picker, for an access that also lets the app delete */
    object FolderAccess : StatusesRequest

    /** Android's question before it deletes gallery copies another install of the app made */
    class GalleryConsent(val sender: IntentSender) : StatusesRequest
}

/** Shows the statuses of one WhatsApp app at a time, saves the chosen ones to the gallery and deletes the unwanted ones */
class StatusesViewModel(application: Application) : AndroidViewModel(application) {
    private val statuses = WhatsAppStatuses(application)
    val thumbnails = StatusThumbnails(application)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    private val _state = MutableStateFlow(StatusesUiState())
    val state: StateFlow<StatusesUiState> = _state.asStateFlow()

    /** Short notices for a toast, such as a status that could not be saved */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _requests = MutableSharedFlow<StatusesRequest>(extraBufferCapacity = 2)
    val requests: SharedFlow<StatusesRequest> = _requests.asSharedFlow()

    private var loadJob: Job? = null

    /** Statuses to delete once the folder access allows it, by name, and the ones waiting for Android's question */
    private var waitingAccess: Set<String> = emptySet()
    private var waitingGallery: List<StatusItem> = emptyList()

    /** Looks at the folder again: on opening the screen and on coming back from WhatsApp or from the folder picker */
    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val chosen = _state.value.app
            val result = withContext(Dispatchers.IO) {
                val apps = statuses.installedApps()
                val app = chosen?.takeIf { it in apps }
                    ?: apps.firstOrNull { it.name == prefs.getString(KEY_APP, null) }
                    ?: apps.firstOrNull()
                Loaded(apps, app, app?.let(statuses::list), statuses.savedNames(), app?.let(statuses::canDelete) == true)
            }
            _state.update { current ->
                val items = result.items.orEmpty()
                val names = items.mapTo(mutableSetOf()) { it.name }
                current.copy(
                    checked = true, loading = false, apps = result.apps, app = result.app,
                    hasAccess = result.items != null, canDelete = result.canDelete, items = items, saved = result.saved,
                    // A status WhatsApp deleted in the meantime closes the full-screen view and leaves the choice
                    viewing = current.viewing?.takeIf { it in names },
                    chosen = current.chosen intersect names,
                )
            }
        }
    }

    fun selectApp(app: WhatsAppApp) {
        if (app == _state.value.app) return
        prefs.edit { putString(KEY_APP, app.name) }
        _state.update { it.copy(app = app, hasAccess = false, items = emptyList(), viewing = null, choosing = false, chosen = emptySet()) }
        refresh()
    }

    fun setFilter(filter: StatusFilter) = _state.update { it.copy(filter = filter) }

    fun pickerStart(): Uri? = _state.value.app?.let(statuses::pickerStart)

    /** The folder the user chose in Android's folder picker for the app being shown */
    fun folderPicked(tree: Uri) {
        val app = _state.value.app ?: return
        val waiting = waitingAccess
        waitingAccess = emptySet()
        viewModelScope.launch {
            val accepted = withContext(Dispatchers.IO) { runCatching { statuses.accept(app, tree) }.getOrDefault(false) }
            if (!accepted) _notices.tryEmit(text(R.string.vd_statuses_wrong_folder, app.label))
            if (accepted && waiting.isNotEmpty()) {
                // The statuses are looked up again, as the new access may lead to them along another route
                val items = withContext(Dispatchers.IO) { if (statuses.canDelete(app)) statuses.list(app) else null }
                if (items != null) {
                    _state.update { it.copy(canDelete = true) }
                    delete(items.filter { it.name in waiting })
                } else {
                    _notices.tryEmit(text(R.string.vd_statuses_no_delete_access))
                }
            }
            refresh()
        }
    }

    /** The folder picker closed without a folder */
    fun accessCancelled() {
        if (waitingAccess.isEmpty()) return
        waitingAccess = emptySet()
        _notices.tryEmit(text(R.string.vd_statuses_no_delete_access))
    }

    fun view(item: StatusItem?) = _state.update { it.copy(viewing = item?.name) }

    fun save(item: StatusItem) {
        val current = _state.value
        if (item.name in current.saved || item.name in current.saving) return
        _state.update { it.copy(saving = it.saving + item.name) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { statuses.save(item) } }
            _state.update {
                it.copy(saving = it.saving - item.name, saved = if (result.isSuccess) it.saved + item.name else it.saved)
            }
            when (result.exceptionOrNull()) {
                null -> Unit
                is FileNotFoundException -> {
                    _notices.tryEmit(text(R.string.vd_statuses_gone))
                    refresh()
                }
                else -> _notices.tryEmit(text(R.string.vd_statuses_save_failed))
            }
        }
    }

    fun startChoosing() = _state.update { it.copy(choosing = true) }

    /** A long press, or a tap while choosing; a long press also starts choosing */
    fun toggle(item: StatusItem) = _state.update {
        it.copy(choosing = true, chosen = if (item.name in it.chosen) it.chosen - item.name else it.chosen + item.name)
    }

    fun chooseAll(items: List<StatusItem>) = _state.update { it.copy(chosen = it.chosen + items.map(StatusItem::name)) }

    fun stopChoosing() = _state.update { it.copy(choosing = false, chosen = emptySet()) }

    fun saveAll(items: List<StatusItem>) {
        items.forEach(::save)
        stopChoosing()
    }

    /**
     * Deletes [items] from WhatsApp's folder and their copies from the gallery, once the user confirmed it. A status
     * leaves WhatsApp's folder only after its copy is gone, so it never stays in the gallery without showing here.
     */
    fun delete(items: List<StatusItem>) {
        if (items.isEmpty()) return
        if (!_state.value.canDelete) {
            // Access given before deleting existed only reads: the folder picker gives it again, then deleting goes on
            waitingAccess = items.mapTo(mutableSetOf()) { it.name }
            _requests.tryEmit(StatusesRequest.FolderAccess)
            return
        }
        viewModelScope.launch {
            val names = items.mapTo(mutableSetOf()) { it.name }
            val refused = withContext(Dispatchers.IO) { runCatching { statuses.deleteCopies(names) } }.getOrElse {
                _notices.tryEmit(text(R.string.vd_statuses_delete_failed))
                return@launch
            }
            deleteFromWhatsApp(items.filterNot { it.name in refused })
            if (refused.isEmpty()) return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                waitingGallery = items.filter { it.name in refused }
                val resolver = getApplication<Application>().contentResolver
                _requests.tryEmit(StatusesRequest.GalleryConsent(MediaStore.createDeleteRequest(resolver, refused.values.toList()).intentSender))
            } else {
                _notices.tryEmit(text(R.string.vd_statuses_not_deleted))
            }
        }
    }

    /** The user's answer to Android's question about the gallery copies; when allowed, Android has deleted them itself */
    fun galleryConsent(allowed: Boolean) {
        val items = waitingGallery
        waitingGallery = emptyList()
        if (items.isEmpty()) return
        if (allowed) viewModelScope.launch { deleteFromWhatsApp(items) } else _notices.tryEmit(text(R.string.vd_statuses_not_deleted))
    }

    private suspend fun deleteFromWhatsApp(items: List<StatusItem>) {
        if (items.isEmpty()) return
        val deleted = withContext(Dispatchers.IO) {
            items.filter { runCatching { statuses.delete(it) }.getOrDefault(false) }.mapTo(mutableSetOf()) { it.name }
        }
        _state.update { current ->
            current.copy(
                items = current.items.filterNot { it.name in deleted },
                saved = current.saved - items.mapTo(mutableSetOf()) { it.name },
                viewing = nextViewing(current.shown.map { it.name }, current.viewing, deleted),
                choosing = false,
                chosen = emptySet(),
            )
        }
        if (deleted.isNotEmpty()) {
            val resources = getApplication<Application>().resources
            _notices.tryEmit(resources.getQuantityString(R.plurals.vd_statuses_deleted, deleted.size, deleted.size))
        }
        if (deleted.size < items.size) _notices.tryEmit(text(R.string.vd_statuses_delete_failed))
        refresh()
    }

    private class Loaded(
        val apps: List<WhatsAppApp>,
        val app: WhatsAppApp?,
        val items: List<StatusItem>?,
        val saved: Set<String>,
        val canDelete: Boolean,
    )

    private fun text(resource: Int, vararg args: Any) = getApplication<Application>().getString(resource, *args)

    private companion object {
        const val KEY_APP = "vd_statuses_app"
    }
}

/** The status the full-screen view shows after [deleted] leave [shown]: the next one, or the one before at the end */
internal fun nextViewing(shown: List<String>, viewing: String?, deleted: Set<String>): String? {
    if (viewing == null || viewing !in deleted) return viewing
    val index = shown.indexOf(viewing)
    if (index < 0) return null
    return shown.drop(index + 1).firstOrNull { it !in deleted } ?: shown.take(index).lastOrNull { it !in deleted }
}
