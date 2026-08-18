package com.estatedesk.crm.ui.screens

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.StageDef

class PipelineStagesActivity : BaseActivity() {

    private var isDeal = false
    private var stages = mutableListOf<StageDef>()

    override fun build() {
        isDeal = intent.getBooleanExtra("deal", false)
        topBar(if (isDeal) getString(R.string.settings_deal_stages) else getString(R.string.settings_pipeline))
        stages = (if (isDeal) Di.store.dealStages() else Di.store.leadStages()).toMutableList()
        render()
    }

    private fun render() {
        content.removeAllViews()
        topBar(if (isDeal) getString(R.string.settings_deal_stages) else getString(R.string.settings_pipeline))
        stages.forEachIndexed { i, s ->
            val card = Ui.card(this, emptyList(), padding = 12)
            val row = Ui.hbox(this)
            row.addView(Ui.tv(this@PipelineStagesActivity, "${i + 1}.", 15, p.textTertiary, Ui.Font.MEDIUM))
            row.addView(Ui.hspacer(this, 10))
            row.addView(Ui.tv(this@PipelineStagesActivity, s.name, 15, p.textPrimary, Ui.Font.MEDIUM, 2),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.tv(this@PipelineStagesActivity, "${s.probability}%", 14, p.textSecondary, Ui.Font.BOLD))
            card.addView(row)
            val actions = Ui.hbox(this)
            val rename = Ui.btn(this@PipelineStagesActivity, getString(R.string.rename), Ui.Btn.TEXT) {
                Ui.prompt(this, getString(R.string.rename), s.name, s.name) { name ->
                    if (name.isNotBlank()) {
                        stages[i] = s.copy(name = name)
                        render()
                    }
                }
            }
            actions.addView(rename)
            val prob = Ui.btn(this@PipelineStagesActivity, getString(R.string.stage_probability), Ui.Btn.TEXT) {
                Ui.prompt(this, getString(R.string.stage_probability), "10", s.probability.toString(), okText = getString(R.string.save)) { v ->
                    stages[i] = s.copy(probability = v.toIntOrNull()?.coerceIn(0, 100) ?: s.probability)
                    render()
                }
            }
            actions.addView(prob)
            if (stages.size > 2) {
                val del = Ui.btn(this@PipelineStagesActivity, getString(R.string.remove), Ui.Btn.TEXT).apply {
                    setTextColor(p.danger)
                    setOnClickListener {
                        Ui.alert(this@PipelineStagesActivity, getString(R.string.stage_remove_title),
                            getString(R.string.stage_remove_msg), getString(R.string.remove)) {
                            val removed = stages.removeAt(i)
                            Async.write({
                                if (isDeal) {
                                    val names = Di.store.dealStages().map { it.name }
                                    // reassign deals to first stage
                                    val to = stages.firstOrNull()?.name ?: "New"
                                    Di.store.deals.reassignStage(removed.name, to)
                                } else {
                                    Di.store.leads.reassignStage(removed.name, stages.firstOrNull()?.name ?: "New Lead")
                                }
                                persist()
                            }) { render() }
                        }
                    }
                }
                actions.addView(del)
            }
            card.addView(actions)
            val up = Ui.icon(this@PipelineStagesActivity, R.drawable.ic_upload, 18, p.textSecondary).apply {
                setOnClickListener {
                    if (i > 0) {
                        val tmp = stages[i]; stages[i] = stages[i - 1]; stages[i - 1] = tmp
                        render()
                    }
                }
            }
            Ui.pad(up, 8, 8, 8, 8)
            card.addView(up)
            add(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@PipelineStagesActivity, 10)
            })
        }
        add(Ui.btn(this@PipelineStagesActivity, "+ " + getString(R.string.stage_add), Ui.Btn.SECONDARY) {
            Ui.prompt(this, getString(R.string.stage_new_stage), getString(R.string.stage_new_stage)) { name ->
                if (name.isNotBlank()) {
                    stages.add(StageDef(name, 20, ""))
                    render()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@PipelineStagesActivity, 10)
        })
        add(Ui.btn(this@PipelineStagesActivity, getString(R.string.save), Ui.Btn.PRIMARY) {
            Async.write({ persist() }) {
                snack(getString(R.string.saved))
                finish()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun persist() {
        if (isDeal) Di.store.saveDealStages(stages) else Di.store.saveLeadStages(stages)
    }
}
