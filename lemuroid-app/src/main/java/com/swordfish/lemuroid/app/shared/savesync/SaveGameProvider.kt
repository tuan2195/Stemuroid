package com.swordfish.lemuroid.app.shared.savesync

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File

// See: https://developer.android.com/guide/topics/providers/create-document-provider#kotlin
class SaveGameProvider : DocumentsProvider() {

    private lateinit var directoryManager: DirectoriesManager

    companion object {
        const val ROOT_ID = "internal_data"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    override fun onCreate(): Boolean {
        directoryManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            DirectoriesManager(requireContext())
        } else {
            context?.let { DirectoriesManager(it) } ?: return false
        }
        return true
    }


    override fun queryRoots(projection: Array<String>?): Cursor {
        val columnFlags = DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, context?.getString(R.string.lemuroid_name))
            add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.lemuroid_launcher)
            add(DocumentsContract.Root.COLUMN_FLAGS, columnFlags)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        addFileToCursor(resolveId(documentId), cursor)
        return cursor
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, queryArgs: String?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        resolveId(parentDocumentId).listFiles()?.forEach { addFileToCursor(it, cursor) }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? {
        val file = resolveId(documentId)
        if (!file.canWrite() && (mode == "rw" || mode == "w" || mode == "a" || mode == "t")) {
            throw UnsupportedOperationException("File $documentId is not writeable!")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun openDocumentThumbnail(
        documentId: String, sizeHint: android.graphics.Point, signal: CancellationSignal
    ): AssetFileDescriptor {
        val descriptor = openDocument(documentId, "r", signal)
        return AssetFileDescriptor(descriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = resolveId(parentDocumentId)
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            File(parent, displayName).mkdirs()
        } else {
            File(parent, displayName).createNewFile()
        }
        return "$parentDocumentId/$displayName"
    }

    override fun deleteDocument(documentId: String) {
        resolveId(documentId).delete()
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        val file = resolveId(documentId)
        val new = File(file.parent, displayName)
        file.renameTo(new)
        return null
    }

    private fun addFileToCursor(file: File, cursor: MatrixCursor) {
        if (file == directoryManager.getInternalRomsDirectory()) return

        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, generateId(file))
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, getType(file))
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, determineName(file))
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, determineFlags(file))
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        }
    }

    private fun determineFlags(file: File): Int {
        var flags = 0

        if (file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        }

        if (file.isDirectory) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        }

        return flags
    }

    private fun determineName(file: File) = when (file) {
        directoryManager.getStatesDirectory() -> "States"
        directoryManager.getSavesDirectory() -> "Saves"
        directoryManager.getInternalRomsDirectory() -> "ROMs"
        directoryManager.getStatesPreviewDirectory() -> "Previews"
        else -> file.name
    }

    private fun generateId(file: File): String = ROOT_ID + "/" + file.toRelativeString(getRoot())

    private fun resolveId(id: String): File {
        if (id == ROOT_ID) return getRoot()
        val localId = id.removePrefix("$ROOT_ID/")
        return getRoot().resolve(localId)
    }

    private fun getRoot(): File = directoryManager.getBaseDirectory()

    private fun getType(file: File): String {
        val mime = MimeTypeMap.getSingleton()
        return if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else if (mime.hasMimeType(file.extension)) {
            mime.getMimeTypeFromExtension(file.extension).toString()
        } else {
            "application/octet-stream"
        }
    }
}
