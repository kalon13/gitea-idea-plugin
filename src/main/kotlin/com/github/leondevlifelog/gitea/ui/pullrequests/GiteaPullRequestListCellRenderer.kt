/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssueListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.gitnex.tea4j.v2.models.PullRequest
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Two lines per pull request: state, number, title and labels; then branches, author, date and comments.
 */
class GiteaPullRequestListCellRenderer : ListCellRenderer<PullRequest> {

    private val title = SimpleColoredComponent()
    private val info = SimpleColoredComponent()
    private val panel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(4, 6)
        add(title, BorderLayout.NORTH)
        add(info, BorderLayout.SOUTH)
    }

    override fun getListCellRendererComponent(
        list: JList<out PullRequest>, value: PullRequest, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val background = UIUtil.getListBackground(isSelected, cellHasFocus)
        val foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
        val grey = UIUtil.getContextHelpForeground()
        panel.background = background
        listOf(title, info).forEach {
            it.clear()
            it.background = background
            it.isOpaque = false
        }

        val state = GiteaPullRequestState.of(value)
        title.append("● ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, state.color))
        title.append("#${value.number} ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, grey))
        title.append(value.title.orEmpty(), SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground))
        if (state != GiteaPullRequestState.OPEN) {
            title.append("  ${state.text}", SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, state.color))
        }
        value.labels.orEmpty().forEach { GiteaIssueListCellRenderer.appendLabel(title, it) }

        val small = SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, grey)
        info.append("${value.head?.label ?: value.head?.ref.orEmpty()} → ${value.base?.ref.orEmpty()}  ·  ", small)
        val date = value.createdAt?.let { DateFormatUtil.formatPrettyDateTime(it) }.orEmpty()
        info.append(GiteaBundle.message("issue.list.opened.by", date, value.user?.login.orEmpty()), small)
        val comments = value.comments ?: 0
        if (comments > 0) {
            info.append("  ·  " + GiteaBundle.message("issue.list.comments", comments), small)
        }
        return panel
    }
}
