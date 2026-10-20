/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.api.GiteaRepositoryClient
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gitnex.tea4j.v2.models.PullRequest

/**
 * Creation of a pull request from the current branch: proposal of title and description, push, creation.
 */
object GiteaPullRequestCreator {

    data class Proposal(val title: String, val body: String)

    private val ISSUE_BRANCH = Regex("""^issue-(\d+)(?:-|$)""")

    /** Issue number of a branch created by "Start Working" (`issue-12-...`). */
    fun issueNumber(branch: String): Long? = ISSUE_BRANCH.find(branch)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * For a branch of an issue: the issue title and "Closes #n", so that merging closes the issue.
     * Otherwise the subject of the first commit of the branch.
     */
    fun propose(branch: String, issueTitle: String?, firstCommitSubject: String?): Proposal {
        val issue = issueNumber(branch)
        return if (issue != null) {
            Proposal(issueTitle ?: firstCommitSubject ?: branch, "Closes #$issue")
        } else {
            Proposal(firstCommitSubject ?: branch, "")
        }
    }

    /** Proposal for [branch] into [base], best effort: failures fall back to the branch name. */
    suspend fun loadProposal(
        project: Project, mapping: GiteaRepositoryMapping, client: GiteaRepositoryClient, branch: String, base: String
    ): Proposal {
        val issueTitle = issueNumber(branch)?.let { number -> optional { client.getIssue(number).title } }
        return propose(branch, issueTitle, optional { firstCommitSubject(project, mapping, base) })
    }

    private suspend fun firstCommitSubject(project: Project, mapping: GiteaRepositoryMapping, base: String): String? =
        withContext(Dispatchers.IO) {
            coroutineToIndicator {
                val handler = GitLineHandler(project, mapping.gitRepository.root, GitCommand.LOG).apply {
                    addParameters("--format=%s", "--reverse", "${mapping.remote.name}/$base..HEAD")
                }
                Git.getInstance().runCommand(handler).takeIf { it.success() }?.output?.firstOrNull { it.isNotBlank() }
            }
        }

    /**
     * Pushes the current branch to the remote of [mapping] as [branch], setting the upstream. Nothing is sent
     * when the remote branch is up to date; a diverged remote branch makes the push fail.
     */
    suspend fun push(project: Project, mapping: GiteaRepositoryMapping, branch: String) {
        withBackgroundProgress(project, GiteaBundle.message("pull.create.push.progress", branch)) {
            withContext(Dispatchers.IO) {
                coroutineToIndicator {
                    val remote = mapping.remote
                    val result = Git.getInstance()
                        .push(mapping.gitRepository, remote.name, remote.firstUrl, "HEAD:refs/heads/$branch", true)
                    if (!result.success()) throw VcsException(result.errorOutputAsJoinedString)
                    mapping.gitRepository.update()
                }
            }
        }
    }

    suspend fun create(
        project: Project, mapping: GiteaRepositoryMapping, client: GiteaRepositoryClient,
        branch: String, base: String, title: String, body: String,
    ): PullRequest {
        push(project, mapping, branch)
        return client.createPullRequest(branch, base, title, body)
    }

    private suspend fun <T> optional(load: suspend () -> T?): T? = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
