package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Files
import com.estatedesk.crm.core.FilesProvider
import com.estatedesk.crm.core.Ui

class ExportActivity : BaseActivity() {

    override fun build() {
        topBar(getString(R.string.export_title))
        add(Ui.caption(this@ExportActivity, getString(R.string.export_choose)))
        add(Ui.spacer(this, 10))
        Di.store.importer.exportSpecs().forEach { spec ->
            val row = Ui.hbox(this)
            row.setPadding(Ui.dp(this@ExportActivity, 12), Ui.dp(this@ExportActivity, 14), Ui.dp(this@ExportActivity, 12), Ui.dp(this@ExportActivity, 14))
            row.background = Ui.pal().let { p ->
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Ui.dp(this@ExportActivity, 12).toFloat()
                    setColor(p.surface)
                }
            }
            row.addView(Ui.tv(this@ExportActivity, spec.label, 15, p.textPrimary, Ui.Font.MEDIUM),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.icon(this@ExportActivity, R.drawable.ic_download, 18, p.primary))
            row.setOnClickListener { export(spec.entity) }
            add(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@ExportActivity, 10)
            })
        }
    }

    private fun export(entity: String) {
        snack(getString(R.string.export_generating))
        Async.db({
            Di.store.importer.exportCsv(entity)
        }) { res ->
            if (res == null || isFinishing) return@db
            val (name, csv) = res
            Async.io({
                val f = Files.uniqueName(Files.exportsDir(this), name.removeSuffix(".csv"), "csv")
                f.writeText(csv)
                f
            }) { f ->
                if (f == null) {
                    snack(getString(R.string.error_generic)); return@io
                }
                share(f)
            }
        }
    }

    private fun share(f: java.io.File) {
        val uri = FilesProvider.uriFor("exports/${f.name}")
        Ui.alert(this, getString(R.string.export_done, f.name), "",
            getString(R.string.export_share), onPositive = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(i, getString(R.string.export_share)))
            })
    }
}
