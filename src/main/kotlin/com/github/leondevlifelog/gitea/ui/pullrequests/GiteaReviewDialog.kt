/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.gitnex.tea4j.v2.models.CreatePullReviewOptions.EventEnum
import javax.swing.ButtonGroup
import javax.swing.JComponent

/**
 * Verdict and text of a review. Gitea does not allow approving or requesting changes on one's own pull request.
 */
class GiteaReviewDialog(project: Project, number: Long, ownPullRequest: Boolean) : DialogWrapper(project) {

    private val textArea = JBTextArea(8, 0).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val approve = JBRadioButton(GiteaBundle.message("pull.review.event.approve")).apply { isEnabled = !ownPullRequest }
    private val requestChanges = JBRadioButton(GiteaBundle.message("pull.review.event.changes")).apply { isEnabled = !ownPullRequest }
    private val comment = JBRadioButton(GiteaBundle.message("pull.review.event.comment"))

    init {
        ButtonGroup().apply {
            add(approve)
            add(requestChanges)
            add(comment)
        }
        (if (ownPullRequest) comment else approve).isSelected = true
        title = GiteaBundle.message("pull.review.title", number)
        setOKButtonText(GiteaBundle.message("pull.review.submit"))
        init()
    }

    override fun createCenterPanel(): DialogPanel = panel {
        row { scrollCell(textArea).align(Align.FILL) }.resizableRow()
        row { cell(approve) }
        row { cell(requestChanges) }
        row { cell(comment) }
    }.apply { preferredSize = JBUI.size(500, 300) }

    override fun getPreferredFocusedComponent(): JComponent = textArea

    override fun doValidate(): ValidationInfo? {
        if (event != EventEnum.APPROVED && text.isEmpty()) {
            return ValidationInfo(GiteaBundle.message("pull.review.text.required"), textArea)
        }
        return null
    }

    val event: EventEnum
        get() = when {
            approve.isSelected -> EventEnum.APPROVED
            requestChanges.isSelected -> EventEnum.REQUEST_CHANGES
            else -> EventEnum.COMMENT
        }

    val text: String get() = textArea.text.trim()
}
