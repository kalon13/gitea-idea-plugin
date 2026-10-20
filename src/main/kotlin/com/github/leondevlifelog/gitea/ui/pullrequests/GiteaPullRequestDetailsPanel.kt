/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.api.GiteaRepositoryClient
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.github.leondevlifelog.gitea.ui.issues.createGiteaHtmlPane
import com.github.leondevlifelog.gitea.ui.issues.loadRenderedComments
import com.github.leondevlifelog.gitea.util.GiteaNotificationIdsHolder
import com.github.leondevlifelog.gitea.util.GiteaNotifications
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.gitnex.tea4j.v2.models.Commit
import org.gitnex.tea4j.v2.models.PullRequest
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.event.DocumentEvent

/**
 * Overview, commits and changes of the selected pull request, with a box to comment and the actions.
 * Must be used from the EDT; [scope] dispatches on the EDT.
 */
class GiteaPullRequestDetailsPanel(
    private val project: Project,
    private val mapping: GiteaRepositoryMapping,
    private val client: GiteaRepositoryClient,
    private val scope: CoroutineScope,
    private val onPullRequestUpdated: (PullRequest) -> Unit,
) : JPanel(BorderLayout()) {

    private val htmlPane = createGiteaHtmlPane()
    private val commitsModel = CollectionListModel<Commit>()
    private val commitsList = JBList(commitsModel).apply {
        cellRenderer = CommitRenderer()
        emptyText.text = GiteaBundle.message("toolwindow.loading")
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) selectedValue?.htmlUrl?.let(BrowserUtil::browse)
            }
        })
    }
    private val filesBrowser = SimpleAsyncChangesBrowser(project, false, false)
    private val tabs = JBTabbedPane().apply {
        addTab(GiteaBundle.message("pull.tab.overview"), JBScrollPane(htmlPane).apply { border = JBUI.Borders.empty() })
        addTab(GiteaBundle.message("pull.tab.commits"), JBScrollPane(commitsList).apply { border = JBUI.Borders.empty() })
        addTab(GiteaBundle.message("pull.tab.files"), filesBrowser)
    }

    private val commentArea = JBTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = GiteaBundle.message("issue.comment.placeholder")
    }
    private val commentButton = JButton(GiteaBundle.message("issue.comment.button")).apply { addActionListener { comment() } }
    private val stateButton = JButton().apply { addActionListener { toggleState() } }
    private val checkoutButton = JButton(GiteaBundle.message("pull.checkout.button")).apply {
        addActionListener { checkout() }
    }
    private val browserButton = JButton(GiteaBundle.message("issue.open.browser")).apply {
        addActionListener { pullRequest?.htmlUrl?.let(BrowserUtil::browse) }
    }
    private val buttons = JPanel(FlowLayout(FlowLayout.LEFT))

    private val emptyPanel = JBPanelWithEmptyText().withEmptyText(GiteaBundle.message("pull.details.select"))
    private val pullRequestPanel = JPanel(BorderLayout()).apply {
        add(tabs, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineTop(JBUI.CurrentTheme.ToolWindow.borderColor())
            add(JBScrollPane(commentArea).apply { border = JBUI.Borders.empty(4) }, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
    }

    private var pullRequest: PullRequest? = null
    private var loadJob: Job? = null
    private var changesJob: Job? = null
    private var changesLoadedFor: Long? = null
    private var busy = false

    init {
        add(emptyPanel, BorderLayout.CENTER)
        buttons.add(commentButton)
        buttons.add(stateButton)
        buttons.add(checkoutButton)
        buttons.add(browserButton)
        // the changes need a git fetch: loaded only when the tab is shown
        tabs.addChangeListener { loadChangesIfShown() }
        commentArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateButtons()
        })
        DumbAwareAction.create { comment() }.registerCustomShortcutSet(CommonShortcuts.getCtrlEnter(), commentArea)
    }

    /** Adds an action button before "Open in Browser". */
    fun addButton(button: JButton) {
        buttons.add(button, buttons.componentCount - 1)
    }

    /** Adds a tab after the built-in ones. */
    fun addTab(title: String, component: JComponent) {
        tabs.addTab(title, component)
    }

    val selectedPullRequest: PullRequest? get() = pullRequest
    val isBusy: Boolean get() = busy

    fun showPullRequest(pullRequest: PullRequest?) {
        if (pullRequest?.number != this.pullRequest?.number) {
            commentArea.text = ""
        }
        this.pullRequest = pullRequest
        loadJob?.cancel()
        removeAll()
        if (pullRequest == null) {
            resetChanges()
            add(emptyPanel, BorderLayout.CENTER)
        } else {
            add(pullRequestPanel, BorderLayout.CENTER)
            setHtml(GiteaPullRequestHtml.build(pullRequest, null, null, null, null))
            commitsModel.removeAll()
            loadJob = scope.launch { loadDetails(pullRequest) }
            resetChanges()
            loadChangesIfShown()
        }
        updateButtons()
        revalidate()
        repaint()
    }

    /** Shows [pullRequest] again if it is the one displayed, after a change made elsewhere. */
    fun pullRequestChanged(pullRequest: PullRequest) {
        if (pullRequest.number == this.pullRequest?.number) showPullRequest(pullRequest)
    }

    private suspend fun loadDetails(pr: PullRequest) {
        try {
            coroutineScope {
                val body = async { client.renderMarkdown(pr.body) }
                val comments = async { loadRenderedComments(client, pr.number) }
                val reviews = async { client.listReviews(pr.number) }
                val status = async { pr.head?.sha?.let { optional { client.getCombinedStatus(it) } } }
                launch { commitsModel.replaceAll(client.getPullRequestCommits(pr.number)) }
                setHtml(GiteaPullRequestHtml.build(pr, body.await(), comments.await(), reviews.await(), status.await()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = StringUtil.escapeXmlEntities(GiteaBundle.message("issue.details.load.error", e.message.orEmpty()))
            setHtml(GiteaPullRequestHtml.build(pr, "<p>$message</p>", null, null, null))
        }
    }

    private fun resetChanges() {
        changesJob?.cancel()
        changesLoadedFor = null
        filesBrowser.setChangesToDisplay(emptyList())
    }

    private fun loadChangesIfShown() {
        val pr = pullRequest ?: return
        if (tabs.selectedComponent !== filesBrowser || changesLoadedFor == pr.number || changesJob?.isActive == true) return
        filesBrowser.viewer.emptyText.text = GiteaBundle.message("pull.files.loading")
        changesJob = scope.launch {
            try {
                filesBrowser.setChangesToDisplay(GiteaPullRequestGit.loadChanges(project, mapping, pr))
                changesLoadedFor = pr.number
                filesBrowser.viewer.emptyText.text = GiteaBundle.message("pull.files.empty")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                filesBrowser.viewer.emptyText.text = GiteaBundle.message("pull.files.error", e.message.orEmpty())
            }
        }
    }

    private fun checkout() {
        val current = pullRequest ?: return
        if (busy) return
        runAction(GiteaBundle.message("pull.checkout.failed")) {
            GiteaPullRequestGit.checkout(project, mapping, current)
        }
    }

    /** Commit statuses are an extra: their failure must not hide the pull request. */
    private suspend fun <T> optional(load: suspend () -> T?): T? = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun setHtml(html: String) {
        htmlPane.text = html
        htmlPane.caretPosition = 0
    }

    private fun comment() {
        val current = pullRequest ?: return
        val text = commentArea.text.trim()
        if (text.isEmpty() || busy) return
        runAction(GiteaBundle.message("issue.comment.failed")) {
            client.addComment(current.number, text)
            commentArea.text = ""
            refresh(current.number)
        }
    }

    private fun toggleState() {
        val current = pullRequest ?: return
        if (busy) return
        val text = commentArea.text.trim()
        val close = GiteaPullRequestState.of(current) != GiteaPullRequestState.CLOSED
        runAction(GiteaBundle.message("pull.state.failed")) {
            if (text.isNotEmpty()) {
                client.addComment(current.number, text)
                commentArea.text = ""
            }
            client.setPullRequestState(current.number, close)
            refresh(current.number)
        }
    }

    /** Reloads the pull request from the server and shows it, in the list too. */
    suspend fun refresh(number: Long) {
        val updated = client.getPullRequest(number)
        onPullRequestUpdated(updated)
        showPullRequest(updated)
    }

    /**
     * Runs a server action with the buttons disabled; failures are notified with [errorTitle].
     */
    fun runAction(errorTitle: String, action: suspend () -> Unit) {
        busy = true
        updateButtons()
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GiteaNotifications.showError(project, GiteaNotificationIdsHolder.PULL_REQUEST_ACTION_FAILED, errorTitle, e)
            } finally {
                busy = false
                updateButtons()
            }
        }
    }

    private val buttonUpdaters = mutableListOf<(PullRequest?) -> Unit>()

    /** Registers the enabling logic of a button added with [addButton]. */
    fun onUpdateButtons(updater: (PullRequest?) -> Unit) {
        buttonUpdaters.add(updater)
    }

    fun updateButtons() {
        val current = pullRequest
        val state = current?.let { GiteaPullRequestState.of(it) }
        val hasComment = commentArea.text.isNotBlank()
        commentButton.isEnabled = current != null && hasComment && !busy
        stateButton.isVisible = state != GiteaPullRequestState.MERGED
        stateButton.isEnabled = current != null && !busy
        stateButton.text = when {
            state == GiteaPullRequestState.CLOSED ->
                GiteaBundle.message(if (hasComment) "pull.reopen.comment.button" else "pull.reopen.button")
            else -> GiteaBundle.message(if (hasComment) "pull.close.comment.button" else "pull.close.button")
        }
        checkoutButton.isEnabled = current != null && !busy
        browserButton.isEnabled = current?.htmlUrl != null
        buttonUpdaters.forEach { it(current) }
    }

    private class CommitRenderer : ListCellRenderer<Commit> {
        private val component = SimpleColoredComponent().apply { border = JBUI.Borders.empty(3, 6) }

        override fun getListCellRendererComponent(
            list: javax.swing.JList<out Commit>, value: Commit, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): java.awt.Component {
            component.clear()
            component.background = UIUtil.getListBackground(isSelected, cellHasFocus)
            component.isOpaque = true
            val subject = value.commit?.message.orEmpty().lineSequence().firstOrNull().orEmpty()
            component.append(subject, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getListForeground(isSelected, cellHasFocus)))
            val author = value.commit?.author?.name ?: value.author?.login.orEmpty()
            val date = value.created?.let { DateFormatUtil.formatPrettyDateTime(it) }.orEmpty()
            component.append(
                "  ${value.sha.orEmpty().take(8)} · $author · $date",
                SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, UIUtil.getContextHelpForeground())
            )
            return component
        }
    }
}
