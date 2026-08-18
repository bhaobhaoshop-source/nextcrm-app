package com.estatedesk.crm.ui.screens

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Files
import com.estatedesk.crm.core.FilesProvider
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.domain.BackupService
import java.io.File

class BackupActivity : BaseActivity() {

    override fun build() {
        topBar(getString(R.string.backup_title))
        val last = Prefs(this).long(Prefs.KEY_LAST_BACKUP, 0)
        add(Ui.tv(this@BackupActivity, if (last > 0) getString(R.string.backup_last, Fmt.dateTime(last))
        else getString(R.string.backup_never), 14, p.textSecondary))
        add(Ui.spacer(this, 8))
        add(Ui.card(this, listOf(Ui.body(this@BackupActivity, getString(R.string.backup_what)))))
        add(Ui.spacer(this, 10))

        add(Ui.btn(this@BackupActivity, getString(R.string.backup_now), Ui.Btn.PRIMARY) { createBackup() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@BackupActivity, 10)
            })
        add(Ui.btn(this@BackupActivity, getString(R.string.backup_restore), Ui.Btn.SECONDARY) { pickRestore() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@BackupActivity, 10)
            })

        // existing backups
        add(Ui.sectionTitle(this, getString(R.string.data_backup)))
        val backups = Files.backupsDir(this).listFiles { f -> f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (backups.isEmpty()) {
            add(Ui.caption(this@BackupActivity, getString(R.string.backup_never)))
        } else {
            backups.take(10).forEach { f ->
                val row = Ui.hbox(this)
                row.setPadding(Ui.dp(this@BackupActivity, 10), Ui.dp(this@BackupActivity, 13), Ui.dp(this@BackupActivity, 10), Ui.dp(this@BackupActivity, 13))
                row.addView(Ui.tv(this@BackupActivity, f.name, 13, p.textPrimary, Ui.Font.MEDIUM, 1),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.tv(this@BackupActivity, Fmt.date(f.lastModified()), 11, p.textTertiary))
                row.addView(Ui.hspacer(this, 8))
                row.addView(Ui.icon(this@BackupActivity, R.drawable.ic_upload, 16, p.primary).apply {
                    setOnClickListener {
                        val uri = FilesProvider.uriFor("backups/${f.name}")
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(i, getString(R.string.share)))
                    }
                })
                add(row)
            }
        }
    }

    private fun createBackup() {
        snack(getString(R.string.export_generating))
        Async.io({
            BackupService.createBackup(this)
        }) { f ->
            if (f == null) {
                snack(getString(R.string.error_generic)); return@io
            }
            snack(getString(R.string.backup_done))
        }
    }

    private fun pickRestore() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, 41)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            Async.io({
                val staging = File(Files.root(this), "restore_pick.zip")
                contentResolver.openInputStream(uri)?.use { ins ->
                    staging.outputStream().use { ins.copyTo(it) }
                }
                if (staging.exists() && BackupService.isValidBackup(staging)) staging else {
                    staging.delete(); null
                }
            }) { f ->
                if (f == null) {
                    Ui.alert(this, getString(R.string.backup_invalid),
                        getString(R.string.backup_invalid), getString(R.string.close)) {}
                    return@io
                }
                Ui.alert(this, getString(R.string.backup_restore),
                    getString(R.string.backup_restore_warn), getString(R.string.confirm)) {
                    Async.io({
                        val ok = BackupService.restore(this, f)
                        f.delete()
                        ok
                    }) { ok ->
                        if (ok == true) {
                            // restart the process to reload the database cleanly
                            android.os.Process.killProcess(android.os.Process.myPid())
                        } else {
                            snack(getString(R.string.error_generic))
                        }
                    }
                }
            }
        }
    }
}
