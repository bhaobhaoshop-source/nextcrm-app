package com.estatedesk.crm.domain

import android.content.Context
import com.estatedesk.crm.core.Files
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.data.Db
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Local backup: zips the SQLite database (WAL-checkpointed) plus all media
 *  into a single portable file. Restore validates the manifest, swaps files
 *  and restarts the process. Cloud sync is intentionally out of scope — the
 *  backup file itself can be stored anywhere the user chooses. */
object BackupService {

    const val MANIFEST_ENTRY = "manifest.json"

    private fun checkpointDb(ctx: Context) {
        try {
            Db.helper(ctx).execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        } catch (t: Throwable) {
        }
    }

    fun createBackup(ctx: Context): File? {
        return try {
            checkpointDb(ctx)
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val out = File(Files.backupsDir(ctx), "estatedesk-backup-$stamp.zip")
            FileOutputStream(out).use { fos ->
                ZipOutputStream(fos).use { zip ->
                    // manifest
                    val counts = arrayOf(
                        "contacts", "leads", "properties", "deals", "tasks", "finances"
                    )
                    val stats = counts.map { it }
                    val manifest = org.json.JSONObject()
                        .put("app", "estatedesk")
                        .put("version", 1)
                        .put("created_at", System.currentTimeMillis())
                        .put("db_version", Db.DB_VERSION)
                        .put("entities", org.json.JSONArray(stats))
                    zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zip.write(manifest.toString().toByteArray())
                    zip.closeEntry()

                    // database file
                    val dbFile = ctx.getDatabasePath("estatedesk.db")
                    if (dbFile.exists()) {
                        zip.putNextEntry(ZipEntry("database.sqlite"))
                        FileInputStream(dbFile).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    // media folders
                    for (dir in listOf(Files.DIR_AVATARS, Files.DIR_PHOTOS, Files.DIR_DOCUMENTS)) {
                        val folder = File(ctx.filesDir, dir)
                        if (!folder.exists()) continue
                        folder.walkTopDown().filter { it.isFile }.forEach { f ->
                            zip.putNextEntry(ZipEntry("$dir/${f.name}"))
                            FileInputStream(f).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            Prefs(ctx).set(Prefs.KEY_LAST_BACKUP, System.currentTimeMillis())
            out
        } catch (t: Throwable) {
            null
        }
    }

    /** Validate that a picked file looks like an EstateDesk backup. */
    fun isValidBackup(file: File): Boolean {
        return try {
            ZipInputStream(FileInputStream(file)).use { zin ->
                var entry: ZipEntry?
                while (true) {
                    entry = zin.nextEntry ?: break
                    if (entry.name == MANIFEST_ENTRY) {
                        val text = zin.readBytes().toString(Charsets.UTF_8)
                        val obj = org.json.JSONObject(text)
                        return obj.optString("app") == "estatedesk"
                    }
                    zin.closeEntry()
                }
                false
            }
        } catch (t: Throwable) {
            false
        }
    }

    /** Restore the backup; returns true if the process will restart. */
    fun restore(ctx: Context, file: File): Boolean {
        if (!isValidBackup(file)) return false
        return try {
            val staging = File(ctx.filesDir, "restore_staging")
            staging.deleteRecursively()
            staging.mkdirs()
            var hasDb = false
            ZipInputStream(FileInputStream(file)).use { zin ->
                var entry: ZipEntry?
                while (true) {
                    entry = zin.nextEntry ?: break
                    val dest = File(staging, entry.name)
                    if (entry.name == MANIFEST_ENTRY) {
                        zin.closeEntry(); continue
                    }
                    if (entry.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile?.mkdirs()
                        FileOutputStream(dest).use { zin.copyTo(it) }
                        if (entry.name == "database.sqlite") hasDb = true
                    }
                    zin.closeEntry()
                }
            }
            if (!hasDb) {
                staging.deleteRecursively()
                return false
            }

            // swap files: close db, replace db + media dirs
            Db.close()
            val dbPath = ctx.getDatabasePath("estatedesk.db")
            val newDb = File(staging, "database.sqlite")
            dbPath.delete()
            newDb.copyTo(dbPath, overwrite = true)
            dbPath.parentFile?.let { p ->
                listOf("estatedesk.db-wal", "estatedesk.db-shm").forEach {
                    File(p, it).delete()
                }
            }
            for (dir in listOf(Files.DIR_AVATARS, Files.DIR_PHOTOS, Files.DIR_DOCUMENTS)) {
                val target = File(ctx.filesDir, dir)
                target.deleteRecursively()
                val src = File(staging, dir)
                if (src.exists()) src.copyRecursively(target, overwrite = true)
                else target.mkdirs()
            }
            staging.deleteRecursively()
            true
        } catch (t: Throwable) {
            false
        }
    }
}
