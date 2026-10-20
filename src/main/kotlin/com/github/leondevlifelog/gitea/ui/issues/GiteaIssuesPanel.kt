/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.api.GiteaIssueFilter
import com.github.leondevlifelog.gitea.api.GiteaIssueState
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
import com.intellij.ui.*
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.gitnex.tea4j.v2.models.Issue
import org.gitnex.tea4j.v2.models.Label
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ItemEvent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * Issues of one repository: filters on top, paginated list on the left, details on the right.
 * Must be used from the EDT; [scope] dispatches on the EDT.
 */
class GiteaIssuesPanel(
    private val project: Project,
    private val mapping: GiteaRepositoryMapping,
    private val client: GiteaRepositoryClient,
    private val scope: CoroutineScope,
) : JPanel(BorderLayout()) {

    private var filter = GiteaIssueFilter()
    private var labels: List<Label> = emptyList()

    private val listModel = CollectionListModel<Issue>()
    private val list = JBList(listModel).apply {
        cellRenderer = GiteaIssueListCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val listScrollPane = JBScrollPane(list).apply { border = JBUI.Borders.empty() }
    private val details = GiteaIssueDetailsPanel(project, client, scope, ::replaceIssue, ::startWorking)

    private val searchField = SearchTextField(false).apply {
        textEditor.emptyText.text = GiteaBundle.message("issue.filter.search.hint")
    }
    private val stateCombo = ComboBox(GiteaIssueState.entries.toTypedArray()).apply {
        renderer = object : SimpleListCellRenderer<GiteaIssueState>() {
            override fun customize(
                list: JList<out GiteaIssueState>, value: GiteaIssueState?, index: Int, selected: Boolean, hasFocus: Boolean
            ) {
                text = value?.let { GiteaBundle.message("issue.filter.state.${it.name.lowercase()}") }.orEmpty()
            }
        }
    }
    private val labelCombo = ComboBox(arrayOf(Choice(null, GiteaBundle.message("issue.filter.label.any"))))
    private val milestoneCombo = ComboBox(arrayOf(Choice(null, GiteaBundle.message("issue.filter.milestone.any"))))
    private val assignedCheck = JBCheckBox(GiteaBundle.message("issue.filter.assigned"))
    private val createdCheck = JBCheckBox(GiteaBundle.message("issue.filter.created"))

    private var page = 0
    private var hasMore = true
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        val toolbar = ActionManager.getInstance().createActionToolbar(
            "GiteaIssues", DefaultActionGroup(
                DumbAwareAction.create(GiteaBundle.message("issue.action.refresh"), AllIcons.Actions.Refresh) { reload() },
                DumbAwareAction.create(GiteaBundle.message("issue.action.new"), AllIcons.General.Add) { createIssue() },
                DumbAwareAction.create(GiteaBundle.message("issue.action.browse.all"), AllIcons.General.Web) {
                    BrowserUtil.browse("${mapping.webUrl}/issues")
                },
            ), true
        ).apply { targetComponent = this@GiteaIssuesPanel }

        val filters = JPanel(BorderLayout()).apply {
            add(JPanel(BorderLayout()).apply {
                add(searchField, BorderLayout.CENTER)
                add(toolbar.component, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                add(stateCombo)
                add(labelCombo)
                add(milestoneCombo)
                add(assignedCheck)
                add(createdCheck)
            }, BorderLayout.SOUTH)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.borderColor()), JBUI.Borders.empty(2, 4, 4, 4)
            )
        }
        add(filters, BorderLayout.NORTH)
        add(OnePixelSplitter(false, "Gitea.Issues.Splitter", 0.4f).apply {
            firstComponent = listScrollPane
            secondComponent = details
        }, BorderLayout.CENTER)

        installListeners()
        installPopup()
        loadFilterValues()
        reload()
    }

    private fun installListeners() {
        list.addListSelectionListener { if (!it.valueIsAdjusting) details.showIssue(list.selectedValue) }
        listScrollPane.verticalScrollBar.model.addChangeListener { loadMoreIfNeeded() }

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(SEARCH_DELAY_MS)
                    updateFilter { it.copy(query = searchField.text) }
                }
            }
        })
        stateCombo.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) updateFilter { f -> f.copy(state = it.item as GiteaIssueState) }
        }
        labelCombo.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) updateFilter { f -> f.copy(label = (it.item as Choice).value) }
        }
        milestoneCombo.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) updateFilter { f -> f.copy(milestone = (it.item as Choice).value) }
        }
        assignedCheck.addActionListener { updateFilter { it.copy(assignedToMe = assignedCheck.isSelected) } }
        createdCheck.addActionListener { updateFilter { it.copy(createdByMe = createdCheck.isSelected) } }
    }

    private fun installPopup() {
        val group = DefaultActionGroup(
            DumbAwareAction.create(GiteaBundle.message("issue.open.browser")) {
                list.selectedValue?.htmlUrl?.let(BrowserUtil::browse)
            },
            DumbAwareAction.create(GiteaBundle.message("issue.action.copy.link")) {
                list.selectedValue?.htmlUrl?.let { CopyPasteManager.getInstance().setContents(StringSelection(it)) }
            },
            DumbAwareAction.create(GiteaBundle.message("issue.start.button")) {
                list.selectedValue?.let(::startWorking)
            },
        )
        PopupHandler.installPopupMenu(list, group, "GiteaIssuesPopup")
    }

    private fun loadFilterValues() {
        scope.launch {
            labels = loadOrEmpty { client.listLabels() }
            labels.forEach { labelCombo.addItem(Choice(it.name, it.name.orEmpty())) }
        }
        scope.launch {
            loadOrEmpty { client.listMilestones() }.forEach { milestoneCombo.addItem(Choice(it.title, it.title.orEmpty())) }
        }
    }

    private suspend fun <T> loadOrEmpty(load: suspend () -> List<T>): List<T> = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun updateFilter(transform: (GiteaIssueFilter) -> GiteaIssueFilter) {
        val newFilter = transform(filter)
        if (newFilter == filter) return
        filter = newFilter
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        page = 0
        hasMore = true
        listModel.removeAll()
        details.showIssue(null)
        loadMore()
    }

    private fun loadMoreIfNeeded() {
        val model = listScrollPane.verticalScrollBar.model
        if (model.value + model.extent >= model.maximum - LOAD_MORE_THRESHOLD) loadMore()
    }

    private fun loadMore() {
        if (!hasMore || loadJob?.isActive == true) return
        list.emptyText.text = GiteaBundle.message("issue.list.loading")
        loadJob = scope.launch {
            list.setPaintBusy(true)
            try {
                val issues = client.listIssues(filter, page + 1, PAGE_SIZE)
                page++
                hasMore = issues.size >= PAGE_SIZE
                listModel.add(issues)
                list.emptyText.text = GiteaBundle.message("issue.list.empty")
                if (filter.issueNumber != null && listModel.size == 1) list.selectedIndex = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hasMore = false
                list.emptyText.text = GiteaBundle.message("issue.list.error", e.message.orEmpty())
            } finally {
                list.setPaintBusy(false)
            }
            // a short first page may not fill the viewport: no scroll event would ever ask for the next one.
            // Checked in a new coroutine, after this job has completed and the list has been laid out.
            if (hasMore) scope.launch { loadMoreIfNeeded() }
        }
    }

    private fun startWorking(issue: Issue) {
        GiteaIssueTaskStarter.start(project, mapping, issue) { branch -> setBranchReference(issue, branch) }
    }

    /** Shows the new branch in the issue on Gitea, so that others know where the work happens. */
    private fun setBranchReference(issue: Issue, branch: String) {
        scope.launch {
            try {
                val updated = client.setIssueRef(issue.number, branch)
                replaceIssue(updated)
                details.issueChanged(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GiteaNotifications.showWarning(
                    project, GiteaNotificationIdsHolder.ISSUE_ACTION_FAILED,
                    GiteaBundle.message("issue.start.reference.failed", branch),
                    GiteaNotifications.getErrorTextFromException(e)
                )
            }
        }
    }

    private fun replaceIssue(issue: Issue) {
        val index = listModel.items.indexOfFirst { it.number == issue.number }
        if (index >= 0) listModel.setElementAt(issue, index)
    }

    private fun createIssue() {
        scope.launch {
            val login = try {
                client.getCurrentUser().login
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val dialog = GiteaCreateIssueDialog(project, mapping.path.toString(), labels, login)
            if (!dialog.showAndGet()) return@launch
            try {
                val created = client.createIssue(dialog.getOption())
                listModel.add(0, created)
                list.selectedIndex = 0
                list.ensureIndexIsVisible(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GiteaNotifications.showError(
                    project, GiteaNotificationIdsHolder.ISSUE_ACTION_FAILED, GiteaBundle.message("issue.create.failed"), e
                )
            }
        }
    }

    private class Choice(val value: String?, val text: String) {
        override fun toString(): String = text
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val LOAD_MORE_THRESHOLD = 50
        private const val SEARCH_DELAY_MS = 400L
    }
}
