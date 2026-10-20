/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.branch.GitBrancher
import git4idea.changes.GitChangeUtils
import git4idea.fetch.GitFetchSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gitnex.tea4j.v2.models.PullRequest

/**
 * Local git operations on a pull request. Gitea publishes the head of every pull request, also from forks,
 * as `refs/pull/<number>/head` in the base repository.
 */
object GiteaPullRequestGit {

    /**
     * Changes of the pull request, from the merge base to its head, as shown by the web UI.
     * Fetches the head into FETCH_HEAD only: no ref is written in the repository.
     */
    suspend fun loadChanges(project: Project, mapping: GiteaRepositoryMapping, pr: PullRequest): Collection<Change> {
        fetch(project, mapping, pullHeadRef(pr))
        val base = pr.mergeBase ?: pr.base.sha
        return withContext(Dispatchers.IO) {
            coroutineToIndicator { GitChangeUtils.getDiff(mapping.gitRepository, base, pr.head.sha, true).orEmpty() }
        }
    }

    /**
     * Checks out the pull request in a local branch.
     * - same repository: the branch of the pull request, tracking its remote branch
     * - fork: `pr-<number>`, created from the head of the pull request
     * An existing local branch is checked out as it is.
     */
    suspend fun checkout(project: Project, mapping: GiteaRepositoryMapping, pr: PullRequest) {
        val repository = mapping.gitRepository
        val remote = mapping.remote.name
        val sameRepository = pr.head?.repoId != null && pr.head.repoId == pr.base?.repoId
        val branch = if (sameRepository) pr.head.ref else "pr-${pr.number}"

        val startPoint = if (sameRepository) {
            fetch(project, mapping, "+refs/heads/$branch:refs/remotes/$remote/$branch")
            "$remote/$branch"
        } else {
            fetch(project, mapping, pullHeadRef(pr))
            pr.head.sha
        }
        withContext(Dispatchers.IO) { repository.update() }

        withContext(Dispatchers.EDT) {
            val brancher = GitBrancher.getInstance(project)
            if (repository.branches.findLocalBranch(branch) != null) {
                brancher.checkout(branch, false, listOf(repository), null)
            } else {
                brancher.checkoutNewBranchStartingFrom(branch, startPoint, listOf(repository), null)
            }
        }
    }

    private fun pullHeadRef(pr: PullRequest) = "refs/pull/${pr.number}/head"

    private suspend fun fetch(project: Project, mapping: GiteaRepositoryMapping, refspec: String) {
        withBackgroundProgress(project, GiteaBundle.message("pull.fetch.progress", mapping.remote.name)) {
            withContext(Dispatchers.IO) {
                coroutineToIndicator {
                    GitFetchSupport.fetchSupport(project).fetch(mapping.gitRepository, mapping.remote, refspec)
                        .throwExceptionIfFailed()
                }
            }
        }
    }
}
