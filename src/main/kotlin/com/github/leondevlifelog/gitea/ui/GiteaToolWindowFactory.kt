/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssuesPanel
import com.github.leondevlifelog.gitea.ui.pullrequests.GiteaPullRequestsPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class GiteaToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val issuesPanel = GiteaRepositoryToolWindowPanel(project) { mapping, client, scope ->
            GiteaIssuesPanel(project, mapping, client, scope)
        }
        val issuesContent = contentFactory.createContent(issuesPanel, GiteaBundle.message("issue"), false)
        issuesContent.setDisposer(issuesPanel)
        toolWindow.contentManager.addContent(issuesContent)

        val pullRequestsPanel = GiteaRepositoryToolWindowPanel(project) { mapping, client, scope ->
            GiteaPullRequestsPanel(project, mapping, client, scope)
        }
        val pullRequestsContent = contentFactory.createContent(pullRequestsPanel, GiteaBundle.message("pr"), false)
        pullRequestsContent.setDisposer(pullRequestsPanel)
        toolWindow.contentManager.addContent(pullRequestsContent)
    }

    override fun shouldBeAvailable(project: Project) = true
}
