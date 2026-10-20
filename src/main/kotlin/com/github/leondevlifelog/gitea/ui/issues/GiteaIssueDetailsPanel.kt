/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.api.GiteaRepositoryClient
import com.github.leondevlifelog.gitea.util.GiteaNotificationIdsHolder
import com.github.leondevlifelog.gitea.util.GiteaNotifications
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.gitnex.tea4j.v2.models.Issue
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Description and comments of the selected issue, with a box to comment and the issue actions.
 * Must be used from the EDT; [scope] dispatches on the EDT.
 */
class GiteaIssueDetailsPanel(
    private val project: Project,
    private val client: GiteaRepositoryClient,
    private val scope: CoroutineScope,
    private val onIssueUpdated: (Issue) -> Unit,
    private val onStartWorking: (Issue) -> Unit,
) : JPanel(BorderLayout()) {

    private val htmlPane = createGiteaHtmlPane()

    private val commentArea = JBTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = GiteaBundle.message("issue.comment.placeholder")
    }

    private val commentButton = JButton(GiteaBundle.message("issue.comment.button")).apply {
        addActionListener { comment() }
    }
    private val stateButton = JButton().apply { addActionListener { toggleState() } }
    private val startButton = JButton(GiteaBundle.message("issue.start.button")).apply {
        addActionListener { issue?.let(onStartWorking) }
    }
    private val browserButton = JButton(GiteaBundle.message("issue.open.browser")).apply {
        addActionListener { issue?.htmlUrl?.let(BrowserUtil::browse) }
    }

    private val emptyPanel = JBPanelWithEmptyText().withEmptyText(GiteaBundle.message("issue.details.select"))
    private val issuePanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(htmlPane).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineTop(JBUI.CurrentTheme.ToolWindow.borderColor())
            add(JBScrollPane(commentArea).apply { border = JBUI.Borders.empty(4) }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(commentButton)
                add(stateButton)
                add(startButton)
                add(browserButton)
            }, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
    }

    private var issue: Issue? = null
    private var loadJob: Job? = null
    private var busy = false

    init {
        add(emptyPanel, BorderLayout.CENTER)
        commentArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateButtons()
        })
        DumbAwareAction.create { comment() }.registerCustomShortcutSet(CommonShortcuts.getCtrlEnter(), commentArea)
    }

    fun showIssue(issue: Issue?) {
        if (issue?.number != this.issue?.number) {
            commentArea.text = ""
        }
        this.issue = issue
        loadJob?.cancel()
        removeAll()
        if (issue == null) {
            add(emptyPanel, BorderLayout.CENTER)
        } else {
            add(issuePanel, BorderLayout.CENTER)
            setHtml(GiteaIssueHtml.build(issue, null, null))
            loadJob = scope.launch { loadDetails(issue) }
        }
        updateButtons()
        revalidate()
        repaint()
    }

    /** Shows [issue] again if it is the one displayed, after a change made elsewhere. */
    fun issueChanged(issue: Issue) {
        if (issue.number == this.issue?.number) showIssue(issue)
    }

    private suspend fun loadDetails(issue: Issue) {
        try {
            coroutineScope {
                val body = async { client.renderMarkdown(issue.body) }
                val comments = loadRenderedComments(client, issue.number)
                setHtml(GiteaIssueHtml.build(issue, body.await(), comments))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = StringUtil.escapeXmlEntities(GiteaBundle.message("issue.details.load.error", e.message.orEmpty()))
            setHtml(GiteaIssueHtml.build(issue, "<p>$message</p>", null))
        }
    }

    private fun setHtml(html: String) {
        htmlPane.text = html
        htmlPane.caretPosition = 0
    }

    private fun comment() {
        val current = issue ?: return
        val text = commentArea.text.trim()
        if (text.isEmpty() || busy) return
        runAction(GiteaBundle.message("issue.comment.failed")) {
            client.addComment(current.number, text)
            commentArea.text = ""
            refresh(current.number)
        }
    }

    private fun toggleState() {
        val current = issue ?: return
        if (busy) return
        val text = commentArea.text.trim()
        runAction(GiteaBundle.message("issue.state.failed")) {
            if (text.isNotEmpty()) {
                client.addComment(current.number, text)
                commentArea.text = ""
            }
            client.setIssueState(current.number, !current.isClosed())
            refresh(current.number)
        }
    }

    private suspend fun refresh(number: Long) {
        val updated = client.getIssue(number)
        onIssueUpdated(updated)
        showIssue(updated)
    }

    private fun runAction(errorTitle: String, action: suspend () -> Unit) {
        busy = true
        updateButtons()
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GiteaNotifications.showError(project, GiteaNotificationIdsHolder.ISSUE_ACTION_FAILED, errorTitle, e)
            } finally {
                busy = false
                updateButtons()
            }
        }
    }

    private fun updateButtons() {
        val current = issue
        val hasComment = commentArea.text.isNotBlank()
        commentButton.isEnabled = current != null && hasComment && !busy
        stateButton.isEnabled = current != null && !busy
        stateButton.text = when {
            current == null -> GiteaBundle.message("issue.close.button")
            current.isClosed() -> GiteaBundle.message(if (hasComment) "issue.reopen.comment.button" else "issue.reopen.button")
            else -> GiteaBundle.message(if (hasComment) "issue.close.comment.button" else "issue.close.button")
        }
        startButton.isEnabled = current != null && !current.isClosed()
        browserButton.isEnabled = current?.htmlUrl != null
    }
}
