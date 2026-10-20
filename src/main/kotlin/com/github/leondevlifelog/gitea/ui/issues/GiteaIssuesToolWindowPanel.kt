/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.api.GiteaRepositoryClient
import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccountManager
import com.github.leondevlifelog.gitea.services.GiteaProjectRepositories
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.github.leondevlifelog.gitea.ui.GiteaSettingsConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * Content of the "Issues" tab: finds the Gitea repositories of the project and shows the issues of the selected one.
 * Follows changes of git remotes and accounts.
 */
class GiteaIssuesToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val scope = GiteaProjectRepositories.getInstance(project)
        .childScope(Dispatchers.EDT + ModalityState.any().asContextElement())

    private var mappingKeys: List<String>? = null
    private var repositoryScope: CoroutineScope? = null
    private val repositoryContent = JPanel(BorderLayout())

    init {
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
            scope.launch { refresh() }
        })
        scope.launch {
            // emits the current accounts immediately, then every change
            service<GiteaAccountManager>().accountsState.collect { refresh() }
        }
    }

    private fun refresh() {
        val mappings = GiteaProjectRepositories.getInstance(project).findMappings()
        val keys = mappings.map { it.key }
        if (keys == mappingKeys) return
        mappingKeys = keys

        removeAll()
        if (mappings.isEmpty()) {
            showRepository(null)
            add(messagePanel(GiteaBundle.message("toolwindow.no.repository")), BorderLayout.CENTER)
        } else {
            if (mappings.size > 1) {
                val combo = ComboBox(mappings.toTypedArray()).apply {
                    renderer = object : SimpleListCellRenderer<GiteaRepositoryMapping>() {
                        override fun customize(
                            list: JList<out GiteaRepositoryMapping>, value: GiteaRepositoryMapping?, index: Int,
                            selected: Boolean, hasFocus: Boolean
                        ) {
                            text = value?.let { "${it.path} (${it.account.server})" }.orEmpty()
                        }
                    }
                    addItemListener { if (it.stateChange == ItemEvent.SELECTED) showRepository(it.item as GiteaRepositoryMapping) }
                }
                add(JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(2, 4)
                    add(combo, BorderLayout.WEST)
                }, BorderLayout.NORTH)
            }
            add(repositoryContent, BorderLayout.CENTER)
            showRepository(mappings.first())
        }
        revalidate()
        repaint()
    }

    private fun showRepository(mapping: GiteaRepositoryMapping?) {
        repositoryScope?.cancel()
        repositoryScope = null
        setRepositoryContent(JBPanelWithEmptyText().withEmptyText(GiteaBundle.message("issue.list.loading")))
        if (mapping == null) return

        val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job))
        repositoryScope = childScope
        childScope.launch {
            val client = GiteaRepositoryClient.create(mapping)
            if (client == null) {
                setRepositoryContent(messagePanel(GiteaBundle.message("toolwindow.missing.token", mapping.account.toString())))
            } else {
                setRepositoryContent(GiteaIssuesPanel(project, mapping, client, childScope))
            }
        }
    }

    private fun setRepositoryContent(component: JComponent) {
        repositoryContent.removeAll()
        repositoryContent.add(component, BorderLayout.CENTER)
        repositoryContent.revalidate()
        repositoryContent.repaint()
    }

    private fun messagePanel(message: String) = JBPanelWithEmptyText().withEmptyText(message).apply {
        emptyText.appendSecondaryText(
            GiteaBundle.message("toolwindow.open.settings"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) { ShowSettingsUtil.getInstance().showSettingsDialog(project, GiteaSettingsConfigurable::class.java) }
    }

    override fun dispose() {
        scope.cancel()
    }
}
