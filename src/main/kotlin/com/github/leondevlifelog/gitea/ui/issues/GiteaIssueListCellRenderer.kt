/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.GiteaBundle
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.gitnex.tea4j.v2.models.Issue
import org.gitnex.tea4j.v2.models.Label
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Two lines per issue: state, number, title and labels; then author, date and comments.
 */
class GiteaIssueListCellRenderer : ListCellRenderer<Issue> {

    private val title = SimpleColoredComponent()
    private val info = SimpleColoredComponent()
    private val panel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(4, 6)
        add(title, BorderLayout.NORTH)
        add(info, BorderLayout.SOUTH)
    }

    override fun getListCellRendererComponent(
        list: JList<out Issue>, value: Issue, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val background = UIUtil.getListBackground(isSelected, cellHasFocus)
        val foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
        panel.background = background
        listOf(title, info).forEach {
            it.clear()
            it.background = background
            it.isOpaque = false
        }

        title.append("● ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, stateColor(value.isClosed())))
        title.append("#${value.number} ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getContextHelpForeground()))
        title.append(value.title.orEmpty(), SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground))
        value.labels.orEmpty().forEach { appendLabel(title, it) }

        val author = value.user?.login.orEmpty()
        val date = value.createdAt?.let { DateFormatUtil.formatPrettyDateTime(it) }.orEmpty()
        info.append(
            GiteaBundle.message("issue.list.opened.by", date, author),
            SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, UIUtil.getContextHelpForeground())
        )
        val comments = value.comments ?: 0
        if (comments > 0) {
            info.append(
                "  ·  " + GiteaBundle.message("issue.list.comments", comments),
                SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, UIUtil.getContextHelpForeground())
            )
        }
        return panel
    }

    companion object {
        val OPEN_COLOR: Color = JBColor(Color(0x1A7F37), Color(0x3FB950))
        val CLOSED_COLOR: Color = JBColor(Color(0x8250DF), Color(0xA371F7))

        fun stateColor(closed: Boolean): Color = if (closed) CLOSED_COLOR else OPEN_COLOR

        /** Background of the label as configured in Gitea, null when unparseable. */
        fun labelColor(label: Label): Color? =
            runCatching { ColorUtil.fromHex(label.color.orEmpty().removePrefix("#")) }.getOrNull()

        fun appendLabel(component: SimpleColoredComponent, label: Label) {
            val bg = labelColor(label) ?: return
            val fg = if (ColorUtil.isDark(bg)) Color.WHITE else Color.BLACK
            component.append("  ")
            component.append(
                " ${label.name} ",
                SimpleTextAttributes(bg, fg, null, SimpleTextAttributes.STYLE_OPAQUE or SimpleTextAttributes.STYLE_SMALLER)
            )
        }
    }
}

fun Issue.isClosed(): Boolean = state == Issue.StateEnum.CLOSED
