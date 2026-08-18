package com.estatedesk.crm.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** App-private file storage helpers. All business files live under
 *  files/ so they survive restarts and can be backed up. */
object Files {

    const val DIR_AVATARS = "avatars"
    const val DIR_PHOTOS = "photos"
    const val DIR_DOCUMENTS = "documents"
    const val DIR_EXPORTS = "exports"
    const val DIR_BACKUPS = "backups"

    fun root(ctx: Context): File = ctx.filesDir

    fun dir(ctx: Context, name: String): File =
        File(root(ctx), name).apply { if (!exists()) mkdirs() }

    fun avatarDir(ctx: Context): File = dir(ctx, DIR_AVATARS)
    fun photosDir(ctx: Context): File = dir(ctx, DIR_PHOTOS)
    fun documentsDir(ctx: Context): File = dir(ctx, DIR_DOCUMENTS)
    fun exportsDir(ctx: Context): File = dir(ctx, DIR_EXPORTS)
    fun backupsDir(ctx: Context): File = dir(ctx, DIR_BACKUPS)

    fun uniqueName(dir: File, base: String, ext: String): File {
        val safeBase = base.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        var f = File(dir, "$safeBase.$ext")
        var i = 1
        while (f.exists()) {
            f = File(dir, "${safeBase}_$i.$ext"); i++
        }
        return f
    }

    /** Copy a content:// stream into app storage. Returns the new path. */
    fun copyIn(ctx: Context, uri: Uri, targetDir: File, baseName: String): File? {
        return try {
            var name = baseName
            var mime = ""
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    val mi = c.getColumnIndex("mime_type")
                    if (mi >= 0) mime = c.getString(mi) ?: ""
                }
            }
            val ext = name.substringAfterLast('.', "").ifBlank { "bin" }
            val dest = uniqueName(targetDir, name.removeSuffix(".$ext"), ext)
            val input: InputStream = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { ins ->
                FileOutputStream(dest).use { outs -> ins.copyTo(outs) }
            }
            dest
        } catch (t: Throwable) {
            null
        }
    }

    fun openExternal(ctx: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ctx.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun deleteQuietly(f: File) {
        try {
            f.delete()
        } catch (t: Throwable) {
        }
    }

    fun sizeOf(f: File): Long = if (f.exists()) f.length() else 0L
}
