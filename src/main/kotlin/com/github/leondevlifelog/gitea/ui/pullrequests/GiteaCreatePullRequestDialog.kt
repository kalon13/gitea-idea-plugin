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
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI

/**
 * Base branch, title and description of a new pull request from [head].
 */
class GiteaCreatePullRequestDialog(
    project: Project,
    private val head: String,
    branches: List<String>,
    defaultBase: String,
    proposal: GiteaPullRequestCreator.Proposal,
) : DialogWrapper(project) {

    private val baseCombo = ComboBox(branches.filter { it != head }.toTypedArray()).apply { selectedItem = defaultBase }
    private var pullTitle = proposal.title
    private var pullBody = proposal.body

    init {
        title = GiteaBundle.message("pull.create.title")
        setOKButtonText(GiteaBundle.message("pull.create.button"))
        init()
    }

    override fun createCenterPanel(): DialogPanel = panel {
        row(GiteaBundle.message("pull.create.field.head")) { label(head) }
        row(GiteaBundle.message("pull.create.field.base")) { cell(baseCombo) }
        row(GiteaBundle.message("issue.create.field.title")) {
            textField().bindText(::pullTitle).align(AlignX.FILL).focused()
                .validationOnApply { if (it.text.isBlank()) error(GiteaBundle.message("issue.create.title.empty")) else null }
        }
        row(GiteaBundle.message("issue.create.field.body")) {
            textArea().bindText(::pullBody).rows(10).align(Align.FILL)
        }.resizableRow()
        row { comment(GiteaBundle.message("pull.create.push.comment")) }
    }.apply { preferredSize = JBUI.size(550, 380) }

    val base: String get() = baseCombo.selectedItem as String
    val pullRequestTitle: String get() = pullTitle.trim()
    val pullRequestBody: String get() = pullBody
}
