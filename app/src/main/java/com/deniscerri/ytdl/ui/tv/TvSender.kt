package com.deniscerri.ytdl.ui.tv

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** What sending to the TV is doing; the sheet shows it */
sealed interface TvSendState {
    object Idle : TvSendState
    object Searching : TvSendState
    class Found(val devices: List<LocalSendDevice>) : TvSendState
    class Waiting(val device: LocalSendDevice, val name: String) : TvSendState
    class Sending(val device: LocalSendDevice, val name: String, val sent: Long, val total: Long) : TvSendState
    class Done(val device: LocalSendDevice, val name: String) : TvSendState
    class Failed(val reason: LocalSendRefusal) : TvSendState
}

/**
 * "Enviar a la TV": finds the TV's LocalSend and sends files there. It runs apart from the screen, so closing the
 * sheet does not stop a file on its way.
 */
object TvSender {
    /** The name the TV shows for this phone */
    private const val ALIAS = "Video Downloader"
    private const val KEY_LAST = "vd_tv_last_localsend"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<TvSendState>(TvSendState.Idle)
    val state: StateFlow<TvSendState> = _state.asStateFlow()
    private var job: Job? = null
    @Volatile private var client: LocalSendClient? = null

    val busy: Boolean get() = state.value.let { it is TvSendState.Waiting || it is TvSendState.Sending }

    fun search(context: Context) {
        val app = context.applicationContext
        job?.cancel()
        _state.value = TvSendState.Searching
        job = scope.launch {
            val devices = runCatching { client(app).discover(app, lastDevice(app)) }.getOrDefault(emptyList())
            _state.value = TvSendState.Found(devices)
        }
    }

    fun send(context: Context, device: LocalSendDevice, files: List<LocalSendFile>) {
        if (files.isEmpty()) return
        val app = context.applicationContext
        job?.cancel()
        remember(app, device)
        val name = files.singleOrNull()?.name ?: files.size.toString()
        val total = files.sumOf { it.size }
        _state.value = TvSendState.Waiting(device, name)
        job = scope.launch {
            var sent = 0L
            val refusal = try {
                client(app).send(device, files, waiting = { _state.value = TvSendState.Waiting(device, name) }) { bytes ->
                    sent += bytes
                    _state.value = TvSendState.Sending(device, name, sent, total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LocalSendRefusal.Failed
            }
            _state.value = if (refusal == null) TvSendState.Done(device, name) else TvSendState.Failed(refusal)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = TvSendState.Idle
    }

    /** The sheet closed: back to the start, unless a file is still on its way */
    fun closed() {
        if (busy) return
        job?.cancel()
        _state.value = TvSendState.Idle
    }

    private fun client(context: Context): LocalSendClient = client ?: synchronized(this) {
        client ?: LocalSendClient(identity(context), ALIAS, Build.MODEL).also { client = it }
    }

    /** Made once and kept, so the TV always sees the same phone */
    private fun identity(context: Context): LocalSendIdentity {
        val file = File(context.filesDir, "localsend_identity")
        return LocalSendIdentity.load(file) ?: LocalSendIdentity.create(ALIAS).also { it.save(file) }
    }

    // The TV is looked for there first next time; its address may change, its fingerprint does not
    private fun remember(context: Context, device: LocalSendDevice) {
        val json = buildJsonObject {
            put("ip", device.ip)
            put("port", device.port)
            put("alias", device.alias)
            put("model", device.model)
            put("fingerprint", device.fingerprint)
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit { putString(KEY_LAST, json.toString()) }
    }

    private fun lastDevice(context: Context): LocalSendDevice? = runCatching {
        val text = PreferenceManager.getDefaultSharedPreferences(context).getString(KEY_LAST, null) ?: return null
        val json = Json.parseToJsonElement(text).jsonObject
        LocalSendDevice(
            ip = json.getValue("ip").jsonPrimitive.content,
            port = json["port"]?.jsonPrimitive?.intOrNull ?: LOCALSEND_PORT,
            alias = json.getValue("alias").jsonPrimitive.content,
            model = json["model"]?.jsonPrimitive?.contentOrNull,
            fingerprint = json.getValue("fingerprint").jsonPrimitive.content,
        )
    }.getOrNull()
}
