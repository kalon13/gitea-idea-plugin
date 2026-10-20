/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.github.leondevlifelog.gitea.tasks.GiteaRepository
import com.github.leondevlifelog.gitea.tasks.GiteaTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.tasks.TaskManager
import git4idea.branch.GitBrancher
import git4idea.validators.GitRefNameValidator
import org.gitnex.tea4j.v2.models.Issue

/**
 * "Start working" on an issue: creates and checks out a branch and, when a Gitea task server is configured for
 * the same repository, activates the task so that time tracking and state updates work.
 */
object GiteaIssueTaskStarter {

    fun start(project: Project, mapping: GiteaRepositoryMapping, issue: Issue) {
        val branch = Messages.showInputDialog(
            project,
            GiteaBundle.message("issue.start.branch.prompt"),
            GiteaBundle.message("issue.start.title", issue.number),
            null,
            branchName(issue.number, issue.title),
            GitRefNameValidator.getInstance()
        ) ?: return

        findTaskRepository(project, mapping)?.let { taskRepository ->
            TaskManager.getManager(project).activateTask(GiteaTask(taskRepository, issue), false)
        }
        GitBrancher.getInstance(project).checkoutNewBranch(branch, listOf(mapping.gitRepository))
    }

    private fun findTaskRepository(project: Project, mapping: GiteaRepositoryMapping): GiteaRepository? {
        return TaskManager.getManager(project).allRepositories.filterIsInstance<GiteaRepository>().firstOrNull {
            it.repoAuthor.equals(mapping.path.owner, true) && it.repoName.equals(mapping.path.repository, true)
        }
    }

    /**
     * `issue-12-fix-login-page`: ascii letters and digits of the title, at most [MAX_SLUG] characters.
     */
    fun branchName(number: Long, title: String?): String {
        val slug = title.orEmpty().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_SLUG)
            .trimEnd('-')
        return if (slug.isEmpty()) "issue-$number" else "issue-$number-$slug"
    }

    private const val MAX_SLUG = 40
}
