/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.GiteaBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.gitnex.tea4j.v2.models.Issue
import java.awt.Color
import java.util.*
import javax.swing.text.html.StyleSheet

/**
 * Builds the HTML shown in the issue details. Markdown is rendered by the server; this only assembles the page.
 */
object GiteaIssueHtml {

    data class RenderedComment(val author: String, val date: Date?, val html: String)

    fun build(issue: Issue, bodyHtml: String?, comments: List<RenderedComment>?): String {
        val grey = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())
        val sb = StringBuilder("<html><body>")

        // headings are not scaled by the IDE html kit: explicit font size
        sb.append("<p><font size='+1'><b>").append(escape(issue.title))
            .append("</b> <font color='$grey'>#${issue.number}</font></font></p>")

        val stateText = GiteaBundle.message(if (issue.isClosed()) "issue.state.closed" else "issue.state.open")
        val stateColor = ColorUtil.toHtmlColor(GiteaIssueListCellRenderer.stateColor(issue.isClosed()))
        sb.append("<p><b><font color='$stateColor'>").append(escape(stateText)).append("</font></b>")
        sb.append(" <font color='$grey'>· ")
            .append(escape(GiteaBundle.message("issue.list.opened.by", formatDate(issue.createdAt), issue.user?.login.orEmpty())))
            .append("</font>")
        issue.milestone?.let {
            sb.append(" <font color='$grey'>· ").append(escape(GiteaBundle.message("issue.details.milestone", it.title)))
                .append("</font>")
        }
        sb.append("</p>")

        val labels = issue.labels.orEmpty()
        if (labels.isNotEmpty()) {
            sb.append("<p>")
            labels.forEach { label ->
                val bg = GiteaIssueListCellRenderer.labelColor(label) ?: Color.GRAY
                val fg = if (ColorUtil.isDark(bg)) "#ffffff" else "#000000"
                sb.append("<span style='background-color:${ColorUtil.toHtmlColor(bg)}; color:$fg'>&nbsp;")
                    .append(escape(label.name)).append("&nbsp;</span> ")
            }
            sb.append("</p>")
        }

        issue.ref?.takeIf { it.isNotBlank() }?.let {
            sb.append("<p><font color='$grey'>").append(escape(GiteaBundle.message("issue.details.branch", it)))
                .append("</font></p>")
        }

        val assignees = issue.assignees.orEmpty().mapNotNull { it.login }
        if (assignees.isNotEmpty()) {
            sb.append("<p><font color='$grey'>")
                .append(escape(GiteaBundle.message("issue.details.assignees", assignees.joinToString(", "))))
                .append("</font></p>")
        }

        sb.append("<hr>")
        when {
            bodyHtml == null -> sb.append("<p><i>").append(escape(GiteaBundle.message("issue.details.loading"))).append("</i></p>")
            bodyHtml.isBlank() -> sb.append("<p><i>").append(escape(GiteaBundle.message("issue.details.no.description"))).append("</i></p>")
            else -> sb.append(sanitize(bodyHtml))
        }

        if (!comments.isNullOrEmpty()) {
            sb.append("<p><b>").append(escape(GiteaBundle.message("issue.details.comments", comments.size))).append("</b></p>")
            comments.forEach { comment ->
                sb.append("<hr><p><b>").append(escape(comment.author)).append("</b> <font color='$grey'>")
                    .append(escape(formatDate(comment.date))).append("</font></p>")
                sb.append(sanitize(comment.html))
            }
        }
        return sb.append("</body></html>").toString()
    }

    private val IMG_TAG = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']*)["'][^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * Images would be loaded synchronously by Swing on the UI thread and attachments need authentication:
     * they are replaced by links.
     */
    fun sanitize(html: String): String {
        return IMG_TAG.replace(html) { "<a href=\"${it.groupValues[1]}\">[${GiteaBundle.message("issue.details.image")}]</a>" }
    }

    /**
     * Styles for the markdown rendered by Gitea. Swing css does not support em/rem: sizes derive from the label font.
     */
    fun addMarkdownRules(styleSheet: StyleSheet) {
        val base = UIUtil.getLabelFont().size
        val codeBackground = ColorUtil.toHtmlColor(ColorUtil.mix(UIUtil.getPanelBackground(), UIUtil.getLabelForeground(), 0.08))
        val link = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED)
        listOf(
            "h1 { font-size: ${base + 8}px; font-weight: bold; margin-top: 10px; margin-bottom: 4px }",
            "h2 { font-size: ${base + 5}px; font-weight: bold; margin-top: 10px; margin-bottom: 4px }",
            "h3 { font-size: ${base + 3}px; font-weight: bold; margin-top: 8px; margin-bottom: 4px }",
            "h4, h5, h6 { font-size: ${base + 1}px; font-weight: bold; margin-top: 8px; margin-bottom: 4px }",
            "p { margin-top: 4px; margin-bottom: 4px }",
            "ul, ol { margin-left: ${base + 8}px; margin-top: 2px; margin-bottom: 2px }",
            "code { font-family: monospace }",
            "pre { font-family: monospace; background-color: $codeBackground; padding: 6px; margin-top: 4px; margin-bottom: 4px }",
            "blockquote { margin-left: 8px; padding-left: 8px; border-left: 3px solid $codeBackground }",
            "a { color: $link }",
        ).forEach(styleSheet::addRule)
    }

    private fun formatDate(date: Date?): String = date?.let { DateFormatUtil.formatPrettyDateTime(it) }.orEmpty()

    private fun escape(text: String?): String = StringUtil.escapeXmlEntities(text.orEmpty())
}
