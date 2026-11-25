package com.deniscerri.ytdl.whatsapp

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** The two WhatsApp apps, with the folders each one has used for the statuses the user has seen */
/** [shortLabel] is for the side-by-side pills, where the full name does not fit on a narrow phone */
enum class WhatsAppApp(val label: String, val shortLabel: String, val packageName: String, val statusFolders: List<String>) {
    WHATSAPP(
        "WhatsApp", "WhatsApp", "com.whatsapp",
        listOf("Android/media/com.whatsapp/WhatsApp/Media/.Statuses", "WhatsApp/Media/.Statuses"),
    ),
    BUSINESS(
        "WhatsApp Business", "Business", "com.whatsapp.w4b",
        listOf("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses", "WhatsApp Business/Media/.Statuses"),
    ),
}

/** A photo or video status as WhatsApp keeps it; [uri] reads it through the folder access the user gave */
data class StatusItem(val uri: Uri, val name: String, val mimeType: String, val modifiedMs: Long) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

/**
 * Reads the statuses WhatsApp keeps after the user sees them, through access to that one folder given in Android's
 * folder picker, copies the chosen ones to the gallery album [ALBUM], and deletes the ones the user no longer wants.
 */
class WhatsAppStatuses(private val context: Context) {
    private val resolver = context.contentResolver
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun installedApps(): List<WhatsAppApp> = WhatsAppApp.entries.filter { context.isAppInstalled(it.packageName) }

    /** Where the folder picker opens: the statuses folder itself, so the user only has to confirm it */
    fun pickerStart(app: WhatsAppApp): Uri {
        // Before Android 11 WhatsApp may still keep its files in its old folder at the top of the storage
        @Suppress("DEPRECATION")
        val storage = Environment.getExternalStorageDirectory()
        val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) app.statusFolders.first()
        else app.statusFolders.firstOrNull { File(storage, it).isDirectory } ?: app.statusFolders.first()
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:$folder")
    }

    /**
     * Keeps the access the user gave to [tree] when it leads to [app]'s statuses folder: the folder itself or one
     * above it, such as WhatsApp's folder in Android/media. Returns false when the statuses are not in [tree].
     */
    fun accept(app: WhatsAppApp, tree: Uri): Boolean {
        val folder = findStatusesFolder(app, tree) ?: return false
        // Writing lets the user delete statuses from here; the folder picker grants it, but reading is enough to save them
        runCatching { resolver.takePersistableUriPermission(tree, READ_WRITE) }
            .onFailure { resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val previous = prefs.getString(treeKey(app), null)
        prefs.edit {
            putString(treeKey(app), tree.toString())
            putString(folderKey(app), folder)
        }
        // Android keeps a limited number of folder accesses per app; give back the replaced one unless the other app uses it
        val stillUsed = WhatsAppApp.entries.any { prefs.getString(treeKey(it), null) == previous }
        if (previous != null && !stillUsed) {
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(previous), READ_WRITE) }
        }
        return true
    }

    /** Whether the access the user gave also lets the app delete [app]'s statuses; access given before deleting existed only reads */
    fun canDelete(app: WhatsAppApp): Boolean {
        val tree = prefs.getString(treeKey(app), null)?.let(Uri::parse) ?: return false
        return resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
    }

    /** Deletes [item] from WhatsApp's folder, so it leaves the list; false when Android did not delete it */
    fun delete(item: StatusItem): Boolean = try {
        DocumentsContract.deleteDocument(resolver, item.uri)
    } catch (e: FileNotFoundException) {
        // WhatsApp deleted it first
        true
    }

    /** The statuses of [app], newest first, or null when the app has no access to the folder yet or lost it */
    fun list(app: WhatsAppApp): List<StatusItem>? {
        val tree = prefs.getString(treeKey(app), null)?.let(Uri::parse) ?: return null
        val folder = prefs.getString(folderKey(app), null) ?: return null
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED)
        // The folder can disappear, for example when WhatsApp is reinstalled; giving access again finds the new one
        val cursor = runCatching { resolver.query(children, projection, null, null, null) }.getOrNull() ?: return null
        val items = mutableListOf<StatusItem>()
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(1) ?: continue
                val mimeType = it.getString(2).orEmpty()
                // WhatsApp also keeps a hidden .nomedia file there
                if (name.startsWith(".") || !(mimeType.startsWith("image/") || mimeType.startsWith("video/"))) continue
                items += StatusItem(DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)), name, mimeType, it.getLong(3))
            }
        }
        return items.sortedByDescending { it.modifiedMs }
    }

    /** Names of the statuses already in the album; from Android 10 on only the copies this app made can be seen */
    fun savedNames(): Set<String> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) legacyAlbum().list()?.toSet().orEmpty() else albumCopies().keys

    /**
     * Deletes the album's copies of the statuses called [names]. Returns the copies Android deletes only after asking
     * the user, as it does with the ones another install of the app made.
     */
    fun deleteCopies(names: Set<String>): Map<String, Uri> {
        if (names.isEmpty()) return emptyMap()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            names.map { File(legacyAlbum(), it) }.filter { it.delete() }.forEach {
                // the gallery forgets a deleted file when it looks at it again
                MediaScannerConnection.scanFile(context, arrayOf(it.absolutePath), null, null)
            }
            return emptyMap()
        }
        val refused = mutableMapOf<String, Uri>()
        albumCopies().filterKeys { it in names }.forEach { (name, uri) ->
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                refused[name] = uri
            }
        }
        return refused
    }

    /** The album's photos and videos by name, as entries of the gallery's media store */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun albumCopies(): Map<String, Uri> = buildMap {
        listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).forEach { collection ->
            runCatching {
                resolver.query(
                    collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf("$RELATIVE_ALBUM/"), null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1) ?: continue
                        put(name, ContentUris.withAppendedId(collection, cursor.getLong(0)))
                    }
                }
            }
        }
    }

    /** Copies [item] to the album with its own name, so it can be told apart from the other statuses there */
    fun save(item: StatusItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(item) else saveLegacy(item)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(item: StatusItem) {
        val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_ALBUM)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("The gallery did not accept ${item.name}")
        try {
            val input = resolver.openInputStream(item.uri) ?: throw IOException("Cannot read ${item.name}")
            input.use { source ->
                val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot write ${item.name}")
                output.use { source.copyTo(it) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Android 9 and older: a plain file in the public DCIM folder, which needs the storage permission */
    private fun saveLegacy(item: StatusItem) {
        val album = legacyAlbum()
        if (!album.isDirectory && !album.mkdirs()) throw IOException("Cannot create $album")
        val target = File(album, item.name)
        val input = resolver.openInputStream(item.uri) ?: throw IOException("Cannot read ${item.name}")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(item.mimeType), null)
    }

    @Suppress("DEPRECATION")
    private fun legacyAlbum() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), ALBUM)

    private fun findStatusesFolder(app: WhatsAppApp, tree: Uri): String? {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val rootName = queryOne(DocumentsContract.buildDocumentUriUsingTree(tree, root), Document.COLUMN_DISPLAY_NAME) ?: return null
        return statusesRoutes(app.statusFolders, rootName).firstNotNullOfOrNull { route ->
            route.fold<String, String?>(root) { parent, name -> parent?.let { childFolder(tree, it, name) } }
        }
    }

    private fun queryOne(uri: Uri, column: String): String? = runCatching {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** The document of the folder called [name] inside [parent]; folder names do not depend on case on the phone's storage */
    private fun childFolder(tree: Uri, parent: String, name: String): String? = runCatching {
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), projection, null, null, null)?.use { cursor ->
            var found: String? = null
            while (found == null && cursor.moveToNext()) {
                if (cursor.getString(2) == Document.MIME_TYPE_DIR && cursor.getString(1).equals(name, ignoreCase = true)) {
                    found = cursor.getString(0)
                }
            }
            found
        }
    }.getOrNull()

    companion object {
        /** The gallery album that gets the saved statuses, photos and videos together */
        const val ALBUM = "Estados de WhatsApp"
        // DCIM is the folder where the gallery takes both photos and videos on every Android version
        private const val RELATIVE_ALBUM = "DCIM/$ALBUM"
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        private fun treeKey(app: WhatsAppApp) = "vd_statuses_tree_${app.name}"
        private fun folderKey(app: WhatsAppApp) = "vd_statuses_folder_${app.name}"

        /**
         * The folder names to walk from the folder the user picked, called [picked], down to the statuses folder of
         * [folders]. A folder whose name is part of a known path continues it from there, matching the exact case
         * first; any other folder is taken as the top of the storage.
         */
        fun statusesRoutes(folders: List<String>, picked: String): List<List<String>> = folders.flatMap { path ->
            val names = path.split('/')
            val matches = names.indices
                .filter { names[it].equals(picked, ignoreCase = true) }
                .sortedByDescending { names[it] == picked }
            if (matches.isEmpty()) listOf(names) else matches.map { names.drop(it + 1) }
        }.distinct()
    }
}
