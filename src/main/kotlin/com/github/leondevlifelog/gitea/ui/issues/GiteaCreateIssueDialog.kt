/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.GiteaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import org.gitnex.tea4j.v2.models.CreateIssueOption
import org.gitnex.tea4j.v2.models.Label

/**
 * Title, description, labels and "assign to me" of a new issue.
 */
class GiteaCreateIssueDialog(
    project: Project,
    repository: String,
    private val labels: List<Label>,
    private val currentLogin: String?,
) : DialogWrapper(project) {

    private var issueTitle = ""
    private var issueBody = ""
    private var assignToMe = false
    private val labelList = CheckBoxList<Label>().apply {
        labels.forEach { addItem(it, it.name.orEmpty(), false) }
    }

    init {
        title = GiteaBundle.message("issue.create.title", repository)
        setOKButtonText(GiteaBundle.message("issue.create.button"))
        init()
    }

    override fun createCenterPanel(): DialogPanel = panel {
        row(GiteaBundle.message("issue.create.field.title")) {
            textField().bindText(::issueTitle).align(AlignX.FILL).focused()
                .validationOnApply { if (it.text.isBlank()) error(GiteaBundle.message("issue.create.title.empty")) else null }
        }
        row(GiteaBundle.message("issue.create.field.body")) {
            textArea().bindText(::issueBody).rows(10).align(Align.FILL)
        }.resizableRow()
        if (labels.isNotEmpty()) {
            row(GiteaBundle.message("issue.create.field.labels")) {
                cell(JBScrollPane(labelList).apply { preferredSize = JBUI.size(300, 100) }).align(AlignX.FILL)
            }
        }
        if (currentLogin != null) {
            row {
                checkBox(GiteaBundle.message("issue.create.assign.me")).bindSelected(::assignToMe)
            }
        }
    }.apply { preferredSize = JBUI.size(550, 400) }

    fun getOption(): CreateIssueOption = CreateIssueOption().apply {
        title = issueTitle.trim()
        body = issueBody
        labels = this@GiteaCreateIssueDialog.labels.filter { labelList.isItemSelected(it) }.mapNotNull { it.id }
        if (assignToMe && currentLogin != null) {
            assignees = listOf(currentLogin)
        }
    }
}
