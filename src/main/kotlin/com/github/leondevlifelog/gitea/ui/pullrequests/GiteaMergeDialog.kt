/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import org.gitnex.tea4j.v2.models.MergePullRequestOption.DoEnum
import org.gitnex.tea4j.v2.models.PullRequest
import org.gitnex.tea4j.v2.models.Repository
import javax.swing.JList

/**
 * Merge style, commit title and message, deletion of the branch. Styles and defaults come from the repository.
 */
class GiteaMergeDialog(project: Project, pr: PullRequest, repository: Repository) : DialogWrapper(project) {

    private val styles = allowedStyles(repository)
    private val styleCombo = ComboBox(styles.toTypedArray()).apply {
        selectedItem = defaultStyle(repository)
        renderer = object : SimpleListCellRenderer<DoEnum>() {
            override fun customize(list: JList<out DoEnum>, value: DoEnum?, index: Int, selected: Boolean, hasFocus: Boolean) {
                text = value?.let { GiteaBundle.message("pull.merge.style.${it.value}") }.orEmpty()
            }
        }
        addActionListener { updateFields() }
    }
    private val titleField = JBTextField().apply { emptyText.text = GiteaBundle.message("pull.merge.default.text") }
    private val messageArea = JBTextArea(5, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = GiteaBundle.message("pull.merge.default.text")
    }
    private var deleteBranch = repository.isDefaultDeleteBranchAfterMerge == true

    init {
        title = GiteaBundle.message("pull.merge.title", pr.number)
        setOKButtonText(GiteaBundle.message("pull.merge.button"))
        init()
        updateFields()
    }

    override fun createCenterPanel(): DialogPanel = panel {
        row(GiteaBundle.message("pull.merge.field.style")) { cell(styleCombo) }
        row(GiteaBundle.message("pull.merge.field.title")) { cell(titleField).align(AlignX.FILL) }
        row(GiteaBundle.message("pull.merge.field.message")) {
            scrollCell(messageArea).align(Align.FILL)
        }.resizableRow()
        row { checkBox(GiteaBundle.message("pull.merge.delete.branch")).bindSelected(::deleteBranch) }
    }.apply { preferredSize = JBUI.size(500, 280) }

    /** Rebase and fast-forward keep the original commits: no commit to describe. */
    private fun updateFields() {
        val createsCommit = selectedStyle in listOf(DoEnum.MERGE, DoEnum.REBASE_MERGE, DoEnum.SQUASH)
        titleField.isEnabled = createsCommit
        messageArea.isEnabled = createsCommit
    }

    val selectedStyle: DoEnum get() = styleCombo.selectedItem as DoEnum
    val commitTitle: String get() = if (titleField.isEnabled) titleField.text.trim() else ""
    val commitMessage: String get() = if (messageArea.isEnabled) messageArea.text.trim() else ""
    val deleteBranchAfterMerge: Boolean get() = deleteBranch

    companion object {
        /** Styles enabled in the repository settings, in the order of the web UI. */
        fun allowedStyles(repository: Repository): List<DoEnum> = listOfNotNull(
            DoEnum.MERGE.takeIf { repository.isAllowMergeCommits != false },
            DoEnum.REBASE.takeIf { repository.isAllowRebase == true },
            DoEnum.REBASE_MERGE.takeIf { repository.isAllowRebaseExplicit == true },
            DoEnum.SQUASH.takeIf { repository.isAllowSquashMerge == true },
            DoEnum.FAST_FORWARD_ONLY.takeIf { repository.isAllowFastForwardOnlyMerge == true },
        ).ifEmpty { listOf(DoEnum.MERGE) }

        fun defaultStyle(repository: Repository): DoEnum {
            val allowed = allowedStyles(repository)
            return DoEnum.entries.firstOrNull { it.value == repository.defaultMergeStyle }?.takeIf { it in allowed } ?: allowed.first()
        }
    }
}
