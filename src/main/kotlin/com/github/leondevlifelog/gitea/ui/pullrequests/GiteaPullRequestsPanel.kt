/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.api.GiteaIssueState
import com.github.leondevlifelog.gitea.api.GiteaPullRequestFilter
import com.github.leondevlifelog.gitea.api.GiteaRepositoryClient
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.github.leondevlifelog.gitea.util.GiteaNotificationIdsHolder
import com.github.leondevlifelog.gitea.util.GiteaNotifications
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionListModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.gitnex.tea4j.v2.models.PullRequest
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ItemEvent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Pull requests of one repository: filters on top, paginated list on the left, details on the right.
 * Must be used from the EDT; [scope] dispatches on the EDT.
 */
class GiteaPullRequestsPanel(
    private val project: Project,
    private val mapping: GiteaRepositoryMapping,
    private val client: GiteaRepositoryClient,
    private val scope: CoroutineScope,
) : JPanel(BorderLayout()) {

    private var filter = GiteaPullRequestFilter()

    private val listModel = CollectionListModel<PullRequest>()
    private val list = JBList(listModel).apply {
        cellRenderer = GiteaPullRequestListCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val listScrollPane = JBScrollPane(list).apply { border = JBUI.Borders.empty() }
    val details = GiteaPullRequestDetailsPanel(project, mapping, client, scope, ::replacePullRequest)

    private val stateCombo = ComboBox(GiteaIssueState.entries.toTypedArray()).apply {
        renderer = object : SimpleListCellRenderer<GiteaIssueState>() {
            override fun customize(
                list: JList<out GiteaIssueState>, value: GiteaIssueState?, index: Int, selected: Boolean, hasFocus: Boolean
            ) {
                text = value?.let { GiteaBundle.message("issue.filter.state.${it.name.lowercase()}") }.orEmpty()
            }
        }
    }
    private val createdCheck = JBCheckBox(GiteaBundle.message("issue.filter.created"))
    private val toolbarActions = DefaultActionGroup(
        DumbAwareAction.create(GiteaBundle.message("pull.action.new"), AllIcons.General.Add) { createPullRequest() },
        DumbAwareAction.create(GiteaBundle.message("issue.action.refresh"), AllIcons.Actions.Refresh) { reload() },
        DumbAwareAction.create(GiteaBundle.message("pull.action.browse.all"), AllIcons.General.Web) {
            BrowserUtil.browse("${mapping.webUrl}/pulls")
        },
    )

    private var page = 0
    private var hasMore = true
    private var loadJob: Job? = null

    init {
        val toolbar = ActionManager.getInstance().createActionToolbar("GiteaPullRequests", toolbarActions, true)
            .apply { targetComponent = this@GiteaPullRequestsPanel }

        add(JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                add(stateCombo)
                add(createdCheck)
            }, BorderLayout.CENTER)
            add(toolbar.component, BorderLayout.EAST)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.borderColor()), JBUI.Borders.empty(2, 4, 4, 4)
            )
        }, BorderLayout.NORTH)
        add(OnePixelSplitter(false, "Gitea.PullRequests.Splitter", 0.4f).apply {
            firstComponent = listScrollPane
            secondComponent = details
        }, BorderLayout.CENTER)

        list.addListSelectionListener { if (!it.valueIsAdjusting) details.showPullRequest(list.selectedValue) }
        listScrollPane.verticalScrollBar.model.addChangeListener { loadMoreIfNeeded() }
        stateCombo.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) updateFilter { f -> f.copy(state = it.item as GiteaIssueState) }
        }
        createdCheck.addActionListener { updateFilter { it.copy(createdByMe = createdCheck.isSelected) } }
        PopupHandler.installPopupMenu(list, DefaultActionGroup(
            DumbAwareAction.create(GiteaBundle.message("issue.open.browser")) {
                list.selectedValue?.htmlUrl?.let(BrowserUtil::browse)
            },
            DumbAwareAction.create(GiteaBundle.message("issue.action.copy.link")) {
                list.selectedValue?.htmlUrl?.let { CopyPasteManager.getInstance().setContents(StringSelection(it)) }
            },
        ), "GiteaPullRequestsPopup")

        reload()
    }

    private fun updateFilter(transform: (GiteaPullRequestFilter) -> GiteaPullRequestFilter) {
        val newFilter = transform(filter)
        if (newFilter == filter) return
        filter = newFilter
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        page = 0
        hasMore = true
        listModel.removeAll()
        details.showPullRequest(null)
        loadMore()
    }

    /** Selects [pullRequest], adding it first to the list when it is not loaded, e.g. after creating it. */
    private fun select(pullRequest: PullRequest) {
        var index = listModel.items.indexOfFirst { it.number == pullRequest.number }
        if (index < 0) {
            listModel.add(0, pullRequest)
            index = 0
        }
        list.selectedIndex = index
        list.ensureIndexIsVisible(index)
    }

    /**
     * New pull request from the current branch into the default branch (changeable in the dialog). When a pull
     * request is already open for the branch, it is selected instead.
     */
    private fun createPullRequest() {
        val branch = mapping.gitRepository.currentBranchName
        if (branch == null) {
            warn(GiteaNotificationIdsHolder.PULL_REQUEST_NO_CURRENT_BRANCH, GiteaBundle.message("pull.create.detached"))
            return
        }
        scope.launch {
            try {
                val base = client.getRepository().defaultBranch
                if (branch == base) {
                    warn(GiteaNotificationIdsHolder.PULL_REQUEST_CREATION_ERROR, GiteaBundle.message("pull.create.on.base", base))
                    return@launch
                }
                val existing = client.findOpenPullRequest(base, branch)
                if (existing != null) {
                    select(existing)
                    GiteaNotifications.showInfo(
                        project, GiteaNotificationIdsHolder.PULL_REQUEST_EXISTS,
                        GiteaBundle.message("pull.create.exists.title"), GiteaBundle.message("pull.create.exists", existing.number, branch)
                    )
                    return@launch
                }
                val branches = client.listBranchNames().ifEmpty { listOf(base) }
                val proposal = GiteaPullRequestCreator.loadProposal(project, mapping, client, branch, base)
                val dialog = GiteaCreatePullRequestDialog(project, branch, branches, base, proposal)
                if (!dialog.showAndGet()) return@launch
                select(
                    GiteaPullRequestCreator.create(
                        project, mapping, client, branch, dialog.base, dialog.pullRequestTitle, dialog.pullRequestBody
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GiteaNotifications.showError(
                    project, GiteaNotificationIdsHolder.PULL_REQUEST_CREATION_ERROR, GiteaBundle.message("pull.create.failed"), e
                )
            }
        }
    }

    private fun warn(displayId: String, message: String) {
        GiteaNotifications.showWarning(project, displayId, GiteaBundle.message("pull.create.failed"), message)
    }

    private fun loadMoreIfNeeded() {
        val model = listScrollPane.verticalScrollBar.model
        if (model.value + model.extent >= model.maximum - LOAD_MORE_THRESHOLD) loadMore()
    }

    private fun loadMore() {
        if (!hasMore || loadJob?.isActive == true) return
        list.emptyText.text = GiteaBundle.message("toolwindow.loading")
        loadJob = scope.launch {
            list.setPaintBusy(true)
            try {
                val pullRequests = client.listPullRequests(filter, page + 1, PAGE_SIZE)
                page++
                hasMore = pullRequests.size >= PAGE_SIZE
                listModel.add(pullRequests)
                list.emptyText.text = GiteaBundle.message("pull.list.empty")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hasMore = false
                list.emptyText.text = GiteaBundle.message("pull.list.error", e.message.orEmpty())
            } finally {
                list.setPaintBusy(false)
            }
            // see GiteaIssuesPanel: a short first page does not fill the viewport
            if (hasMore) scope.launch { loadMoreIfNeeded() }
        }
    }

    private fun replacePullRequest(pullRequest: PullRequest) {
        val index = listModel.items.indexOfFirst { it.number == pullRequest.number }
        if (index >= 0) listModel.setElementAt(pullRequest, index)
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val LOAD_MORE_THRESHOLD = 50
    }
}
