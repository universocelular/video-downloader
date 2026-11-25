package com.deniscerri.ytdl.ui.tv

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * LocalSend (github.com/localsend/protocol, Apache 2.0) passes files between devices on the same Wi-Fi without
 * accounts. The TV runs the LocalSend app; this side only sends. Its encrypted mode, the default, asks senders for a
 * certificate of their own and knows each device by the SHA-256 of its certificate, its fingerprint. With encryption
 * turned off there, it speaks plain HTTP instead.
 */

internal const val LOCALSEND_PORT = 53317
private const val MULTICAST_GROUP = "224.0.0.167"
private const val PROTOCOL_VERSION = "2.1"
private const val API = "/api/localsend/v2"

/** A LocalSend receiver on the Wi-Fi, such as the TV; [fingerprint] is the SHA-256 of its certificate when [https] */
data class LocalSendDevice(
    val ip: String,
    val port: Int,
    val alias: String,
    val model: String?,
    val fingerprint: String,
    val https: Boolean = true,
)

/** One file to send: [open] reads it from the start */
class LocalSendFile(val id: String, val name: String, val size: Long, val mimeType: String, val open: () -> InputStream)

/** Why a receiver did not take the files */
enum class LocalSendRefusal { Rejected, Busy, Pin, Failed }

/** This phone as a LocalSend sender: an EC key and a self-signed certificate made for it */
class LocalSendIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {
    val fingerprint: String = sha256Hex(certificate.encoded)

    fun save(file: File) = DataOutputStream(file.outputStream()).use { out ->
        for (part in listOf(certificate.encoded, privateKey.encoded)) {
            out.writeInt(part.size)
            out.write(part)
        }
    }

    companion object {
        private const val TWENTY_YEARS_MS = 20L * 365 * 24 * 60 * 60 * 1000
        private const val OID_ECDSA_SHA256 = "1.2.840.10045.4.3.2"
        private const val OID_COMMON_NAME = "2.5.4.3"

        /** A new identity: an EC P-256 key and a version 3 certificate signed with it, valid for twenty years */
        fun create(name: String, now: Date = Date()): LocalSendIdentity {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val algorithm = Der.sequence(Der.oid(OID_ECDSA_SHA256))
            val owner = Der.sequence(Der.set(Der.sequence(Der.oid(OID_COMMON_NAME), Der.utf8(name))))
            val tbs = Der.sequence(
                Der.explicit(0, Der.integer(BigInteger.valueOf(2))),
                Der.integer(BigInteger(63, SecureRandom()).add(BigInteger.ONE)),
                algorithm,
                owner,
                Der.sequence(Der.utcTime(now), Der.utcTime(Date(now.time + TWENTY_YEARS_MS))),
                owner,
                pair.public.encoded,
            )
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(tbs)
                sign()
            }
            return LocalSendIdentity(pair.private, certificateOf(Der.sequence(tbs, algorithm, Der.bitString(signature))))
        }

        fun load(file: File): LocalSendIdentity? = runCatching {
            DataInputStream(file.inputStream()).use { input ->
                val (certificate, key) = List(2) { ByteArray(input.readInt()).also(input::readFully) }
                LocalSendIdentity(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(key)), certificateOf(certificate))
            }
        }.getOrNull()

        private fun certificateOf(der: ByteArray) =
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }
}

/** The few DER pieces a self-signed certificate needs */
internal object Der {
    private fun element(tag: Int, content: ByteArray): ByteArray {
        val size = content.size
        val length = when {
            size < 0x80 -> byteArrayOf(size.toByte())
            size < 0x100 -> byteArrayOf(0x81.toByte(), size.toByte())
            else -> byteArrayOf(0x82.toByte(), (size shr 8).toByte(), size.toByte())
        }
        return byteArrayOf(tag.toByte()) + length + content
    }

    private fun joined(parts: Array<out ByteArray>) = parts.fold(ByteArray(0)) { all, part -> all + part }

    fun sequence(vararg parts: ByteArray) = element(0x30, joined(parts))
    fun set(vararg parts: ByteArray) = element(0x31, joined(parts))
    fun integer(value: BigInteger) = element(0x02, value.toByteArray())
    fun utf8(text: String) = element(0x0C, text.toByteArray(Charsets.UTF_8))
    fun bitString(bytes: ByteArray) = element(0x03, byteArrayOf(0) + bytes)
    fun explicit(number: Int, content: ByteArray) = element(0xA0 or number, content)

    fun utcTime(date: Date): ByteArray {
        val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return element(0x17, format.format(date).toByteArray(Charsets.US_ASCII))
    }

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map(String::toLong)
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            // base 128, high bit set on every byte but the last
            val groups = generateSequence(arc) { (it shr 7).takeIf { rest -> rest > 0 } }.map { (it and 0x7F).toInt() }.toList().reversed()
            groups.forEachIndexed { i, group -> out.write(if (i < groups.lastIndex) group or 0x80 else group) }
        }
        return element(0x06, out.toByteArray())
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }

/** How this app introduces itself in every LocalSend message */
internal fun senderInfo(alias: String, model: String, fingerprint: String): JsonObject = buildJsonObject {
    put("alias", alias)
    put("version", PROTOCOL_VERSION)
    put("deviceModel", model)
    put("deviceType", "mobile")
    put("fingerprint", fingerprint)
    put("port", LOCALSEND_PORT)
    put("protocol", "https")
    put("download", false)
}

internal fun prepareUploadBody(info: JsonObject, files: List<LocalSendFile>): JsonObject = buildJsonObject {
    put("info", info)
    putJsonObject("files") {
        for (file in files) {
            putJsonObject(file.id) {
                put("id", file.id)
                put("fileName", file.name)
                put("size", file.size)
                put("fileType", file.mimeType)
            }
        }
    }
}

/** A receiver from a multicast message or a register answer, seen at [ip]; null for anything else and for this phone ([own]) */
internal fun parseDevice(text: String, ip: String, own: String): LocalSendDevice? {
    val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    fun field(name: String) = runCatching { json[name]?.jsonPrimitive }.getOrNull()
    val fingerprint = field("fingerprint")?.contentOrNull ?: return null
    if (fingerprint.equals(own, ignoreCase = true)) return null
    return LocalSendDevice(
        ip = ip,
        port = field("port")?.intOrNull ?: LOCALSEND_PORT,
        alias = field("alias")?.contentOrNull ?: ip,
        model = field("deviceModel")?.contentOrNull,
        fingerprint = fingerprint,
        https = (field("protocol")?.contentOrNull ?: "https") == "https",
    )
}

/** The session and each file's token, from the answer to prepare-upload */
internal fun parsePrepareAnswer(text: String): Pair<String, Map<String, String>>? = runCatching {
    val json = Json.parseToJsonElement(text).jsonObject
    val session = json.getValue("sessionId").jsonPrimitive.content
    session to json.getValue("files").jsonObject.mapValues { it.value.jsonPrimitive.content }
}.getOrNull()

internal fun refusalOf(code: Int): LocalSendRefusal = when (code) {
    401 -> LocalSendRefusal.Pin
    403 -> LocalSendRefusal.Rejected
    409, 429 -> LocalSendRefusal.Busy
    else -> LocalSendRefusal.Failed
}

/** The other addresses of a home network (the /24 around [own]), where a receiver may be */
internal fun neighbours(own: String): List<String> {
    val parts = own.split('.')
    if (parts.size != 4) return emptyList()
    val prefix = parts.take(3).joinToString(".")
    return (1..254).map { "$prefix.$it" }.filter { it != own }
}

/** The status and body of a plain HTTP/1.1 answer, sized by Content-Length, sent in chunks, or running to the end */
internal fun readHttpAnswer(input: InputStream): Pair<Int, String> {
    fun line(): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0 || byte == '\n'.code) break
            if (byte != '\r'.code) out.write(byte)
        }
        return out.toString("ISO-8859-1")
    }
    val status = line().split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("Not an HTTP answer")
    val headers = generateSequence { line().takeIf { it.isNotEmpty() } }
        .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
    val body = ByteArrayOutputStream()
    val length = headers["content-length"]?.toLongOrNull()
    when {
        headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> while (true) {
            val size = line().substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            body.write(input.readNBytesCompat(size))
            line()
        }
        length != null -> body.write(input.readNBytesCompat(length.toInt()))
        else -> input.copyTo(body)
    }
    return status to body.toString("UTF-8")
}

private fun InputStream.readNBytesCompat(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var done = 0
    while (done < count) {
        val read = read(bytes, done, count - done)
        if (read < 0) throw IOException("The answer ended early")
        done += read
    }
    return bytes
}

/** What a receiver answered; [certificate] is its own when the connection was encrypted */
private class Answer(val code: Int, val text: String, val certificate: ByteArray? = null)

/** Finds LocalSend receivers on the Wi-Fi and sends files to one of them */
class LocalSendClient(private val identity: LocalSendIdentity, alias: String, model: String) {
    private val info = senderInfo(alias, model, identity.fingerprint)
    private val infoBytes = info.toString().toByteArray()
    private val base = OkHttpClient()

    /** This phone's certificate, shown to every receiver that asks for it */
    private val keyManagers by lazy {
        val keys = KeyStore.getInstance("PKCS12").apply {
            load(null)
            setKeyEntry("localsend", identity.privateKey, CharArray(0), arrayOf(identity.certificate))
        }
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, CharArray(0)) }.keyManagers
    }

    /**
     * Receivers on the Wi-Fi: the [last] one used first, then LocalSend's multicast announcement, and if nothing
     * answers, asking every address of the network as LocalSend itself does when multicast is blocked.
     */
    suspend fun discover(context: Context, last: LocalSendDevice?): List<LocalSendDevice> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, LocalSendDevice>()
        last?.let { register(it.ip, it.port, connectMs = 1500) }?.let { found[it.fingerprint] = it }
        multicast(context).forEach { found.putIfAbsent(it.fingerprint, it) }
        if (found.isEmpty()) {
            val gate = Semaphore(32)
            val nearby = wifiAddress()?.let { neighbours(it.second.hostAddress.orEmpty()) }.orEmpty()
            nearby.map { ip -> async { gate.withPermit { register(ip, LOCALSEND_PORT, connectMs = 700) } } }.awaitAll()
                .filterNotNull()
                .forEach { found.putIfAbsent(it.fingerprint, it) }
        }
        found.values.toList()
    }

    /**
     * Sends [files] to [device]. LocalSend there may first ask the person at the TV, and [waiting] says so; [sent]
     * counts the bytes as they go. Returns null when everything arrived.
     */
    suspend fun send(device: LocalSendDevice, files: List<LocalSendFile>, waiting: () -> Unit, sent: (Long) -> Unit): LocalSendRefusal? =
        withContext(Dispatchers.IO) {
            waiting()
            // The person at the TV may take a while to accept, and a long file a while to arrive
            val prepare = try {
                val body = prepareUploadBody(info, files).toString().toByteArray()
                post(device, "$API/prepare-upload", "application/json", body.size.toLong(), readSeconds = 600) { it.write(body) }
            } catch (e: IOException) {
                return@withContext LocalSendRefusal.Failed
            }
            when (prepare.code) {
                200 -> Unit
                204 -> return@withContext null
                else -> return@withContext refusalOf(prepare.code)
            }
            val (session, tokens) = parsePrepareAnswer(prepare.text) ?: return@withContext LocalSendRefusal.Failed
            try {
                for (file in files) {
                    val token = tokens[file.id] ?: continue
                    val path = "$API/upload?sessionId=${encode(session)}&fileId=${encode(file.id)}&token=${encode(token)}"
                    val upload = post(device, path, file.mimeType, file.size, readSeconds = 600) { out -> copyCounting(file, out, sent) }
                    if (upload.code !in 200..299) return@withContext refusalOf(upload.code)
                }
                null
            } catch (e: Throwable) {
                // Stopped or cut off: the TV closes its side of the transfer too, so it can take the next one
                withContext(NonCancellable) {
                    runCatching { post(device, "$API/cancel?sessionId=${encode(session)}", "application/json", 0, readSeconds = 5) {} }
                }
                if (e is IOException) LocalSendRefusal.Failed else throw e
            }
        }

    /** LocalSend's register call: the receiver at [ip] tells who it is; when encrypted, its certificate proves it */
    private suspend fun register(ip: String, port: Int, connectMs: Long): LocalSendDevice? {
        val encrypted = LocalSendDevice(ip, port, ip, null, fingerprint = "", https = true)
        val answer = try {
            post(encrypted, "$API/register", "application/json", infoBytes.size.toLong(), readSeconds = 4, connectMs = connectMs) { it.write(infoBytes) }
        } catch (e: SSLException) {
            // Something answers there without encryption: LocalSend with encryption turned off
            runCatching {
                post(encrypted.copy(https = false), "$API/register", "application/json", infoBytes.size.toLong(), readSeconds = 4, connectMs = connectMs) { it.write(infoBytes) }
            }.getOrNull()
        } catch (e: IOException) {
            null
        } ?: return null
        if (answer.code !in 200..299) return null
        val device = parseDevice(answer.text, ip, identity.fingerprint) ?: return null
        val certificate = answer.certificate
        return if (certificate != null) device.copy(port = port, fingerprint = sha256Hex(certificate), https = true)
        else device.copy(port = port, https = false)
    }

    /** One POST to [device]: over HTTPS with this phone's certificate, or plain when the receiver has no encryption */
    private suspend fun post(
        device: LocalSendDevice,
        path: String,
        type: String,
        length: Long,
        readSeconds: Long,
        connectMs: Long = 4000,
        body: (OutputStream) -> Unit,
    ): Answer {
        if (!device.https) return plainPost(device, path, type, length, readSeconds, connectMs, body)
        val client = http(expected = device.fingerprint.ifEmpty { null }, connectMs, readSeconds)
        val request = Request.Builder()
            .url("https://${device.ip}:${device.port}$path")
            .post(object : RequestBody() {
                override fun contentType(): MediaType? = runCatching { type.toMediaType() }.getOrNull()
                override fun contentLength() = length
                override fun writeTo(sink: BufferedSink) = body(sink.outputStream())
            })
            .build()
        return client.newCall(request).await().use { response ->
            Answer(response.code, response.body.string(), response.handshake?.peerCertificates?.firstOrNull()?.encoded)
        }
    }

    /**
     * The same POST as plain HTTP over a socket of its own. Android lets this app make plain HTTP connections only to
     * itself, so receivers without encryption are reached without the HTTP library; cancelling closes the socket.
     */
    private suspend fun plainPost(
        device: LocalSendDevice,
        path: String,
        type: String,
        length: Long,
        readSeconds: Long,
        connectMs: Long,
        body: (OutputStream) -> Unit,
    ): Answer = coroutineScope {
        val socket = Socket()
        val work = async(Dispatchers.IO) {
            socket.connect(InetSocketAddress(device.ip, device.port), connectMs.toInt())
            socket.soTimeout = (readSeconds * 1000).toInt()
            val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            val head = "POST $path HTTP/1.1\r\nHost: ${device.ip}:${device.port}\r\nContent-Type: $type\r\n" +
                "Content-Length: $length\r\nConnection: close\r\n\r\n"
            out.write(head.toByteArray(Charsets.US_ASCII))
            body(out)
            out.flush()
            val (code, text) = readHttpAnswer(BufferedInputStream(socket.getInputStream()))
            Answer(code, text)
        }
        try {
            work.await()
        } catch (e: CancellationException) {
            runCatching { socket.close() }
            throw e
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Announces this phone to the LocalSend group; receivers answer there with a message of their own */
    private fun multicast(context: Context, listenMs: Long = 2500): List<LocalSendDevice> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("vd_localsend").apply { setReferenceCounted(false) }
        val devices = mutableListOf<LocalSendDevice>()
        try {
            lock.acquire()
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(LOCALSEND_PORT))
                val group = InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), LOCALSEND_PORT)
                val network = wifiAddress()?.first
                if (network != null) socket.networkInterface = network
                socket.joinGroup(group, network)
                socket.soTimeout = 250
                val message = JsonObject(info + ("announce" to JsonPrimitive(true))).toString().toByteArray()
                socket.send(DatagramPacket(message, message.size, group))
                val buffer = ByteArray(8192)
                val end = System.currentTimeMillis() + listenMs
                while (System.currentTimeMillis() < end) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val ip = packet.address.hostAddress ?: continue
                    parseDevice(String(packet.data, packet.offset, packet.length, Charsets.UTF_8), ip, identity.fingerprint)?.let(devices::add)
                }
            }
        } catch (e: IOException) {
            // The port may be taken by another app; asking each address still finds the TV
        } finally {
            if (lock.isHeld) lock.release()
        }
        return devices.distinctBy { it.fingerprint }
    }

    /** The Wi-Fi network card and this phone's address on it */
    private fun wifiAddress(): Pair<NetworkInterface, Inet4Address>? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.startsWith("wlan") }
            .firstNotNullOfOrNull { card ->
                card.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }?.let { card to it }
            }
    }.getOrNull()

    /**
     * A connection that shows this phone's certificate. With an [expected] fingerprint it reaches only that receiver,
     * recognized by its certificate; without one, any receiver, which then has to prove who it is with its own.
     */
    private fun http(expected: String?, connectMs: Long, readSeconds: Long): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                // LocalSend certificates are self-signed: the fingerprint is what identifies the receiver
                if (expected != null && !sha256Hex(chain.first().encoded).equals(expected, ignoreCase = true)) {
                    throw CertificateException("Not the LocalSend receiver that was chosen")
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val tls = SSLContext.getInstance("TLS").apply { init(keyManagers, arrayOf(trust), SecureRandom()) }
        // Every call shares the threads and connections of one client
        return base.newBuilder()
            .sslSocketFactory(tls.socketFactory, trust)
            // Receivers are reached by address and recognized by fingerprint, not by name
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
            .readTimeout(readSeconds, TimeUnit.SECONDS)
            .writeTimeout(readSeconds, TimeUnit.SECONDS)
            .build()
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Copies the file to the connection, counting the bytes */
    private fun copyCounting(file: LocalSendFile, out: OutputStream, sent: (Long) -> Unit) {
        file.open().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                sent(read.toLong())
            }
        }
    }
}

/** Runs the call so that cancelling the coroutine cancels the request */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    })
}
