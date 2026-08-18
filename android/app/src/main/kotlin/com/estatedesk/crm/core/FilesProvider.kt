package com.estatedesk.crm.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Minimal content provider that safely exposes app-private files (photos,
 *  documents, exports, backups) to other apps for viewing/sharing. */
class FilesProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "*/*", CODE_FILE)
        addURI(AUTHORITY, "*", CODE_FILE)
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val file = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols)
        val row = arrayOfNulls<Any>(cols.size)
        for ((i, col) in cols.withIndex()) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row[i] = file.name
                OpenableColumns.SIZE -> row[i] = file.length()
            }
        }
        c.addRow(row)
        return c
    }

    override fun getType(uri: Uri): String {
        val file = fileFor(uri) ?: return "application/octet-stream"
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "zip" -> "application/zip"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        val access = if (mode.contains("w")) ParcelFileDescriptor.MODE_READ_WRITE
        else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(file, access)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int = 0

    private fun fileFor(uri: Uri): File? {
        val context = context ?: return null
        val parts = uri.pathSegments
        val root = context.filesDir
        return when {
            parts.size == 1 -> File(root, parts[0]).takeIf { it.isInside(root) }
            parts.size >= 2 -> File(root, parts[0] + "/" + parts.drop(1).joinToString("/"))
                .takeIf { it.isInside(root) }
            else -> null
        }
    }

    private fun File.isInside(root: File): Boolean {
        val p = canonicalPath
        val r = root.canonicalPath
        return p == r || p.startsWith(r + "/")
    }

    companion object {
        private const val CODE_FILE = 1
        const val AUTHORITY = "com.estatedesk.crm.files"

        fun uriFor(relativePath: String): Uri =
            Uri.parse("content://$AUTHORITY/$relativePath")
    }
}
