/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccountManager
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.github.leondevlifelog.gitea.services.GiteaSettings
import com.google.gson.JsonParser
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gitnex.tea4j.v2.models.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.awaitResponse

class GiteaApiException(message: String, val statusCode: Int) : Exception(message)

/**
 * Suspending access to the API of one repository. Requests are cancelled together with the calling coroutine.
 */
class GiteaRepositoryClient(private val api: GiteaApi, path: GiteaRepositoryPath) {

    private val owner = path.owner
    private val repo = path.repository

    private var currentUser: User? = null

    suspend fun getCurrentUser(): User {
        currentUser?.let { return it }
        return call(api.getUserApi().userGetCurrent()).also { currentUser = it }
    }

    suspend fun listIssues(filter: GiteaIssueFilter, page: Int, limit: Int): List<Issue> {
        filter.issueNumber?.let { return if (page > 1) emptyList() else listOfNotNull(findIssue(it)) }
        val login = if (filter.assignedToMe || filter.createdByMe) getCurrentUser().login else null
        return call(
            api.getIssueApi().issueListIssues(
                owner, repo,
                filter.state.apiValue,
                filter.label,
                filter.query.trim().ifEmpty { null },
                "issues",
                filter.milestone,
                null, null,
                login.takeIf { filter.createdByMe },
                login.takeIf { filter.assignedToMe },
                null,
                page, limit
            )
        )
    }

    suspend fun getIssue(number: Long): Issue = call(api.getIssueApi().issueGetIssue(owner, repo, number))

    /** @return null when there is no issue with this number, or it is a pull request */
    private suspend fun findIssue(number: Long): Issue? = try {
        getIssue(number).takeIf { it.pullRequest == null }
    } catch (e: GiteaApiException) {
        if (e.statusCode == 404) null else throw e
    }

    suspend fun getComments(number: Long): List<Comment> =
        call(api.getIssueApi().issueGetComments(owner, repo, number, null, null))

    suspend fun addComment(number: Long, body: String): Comment =
        call(api.getIssueApi().issueCreateComment(owner, repo, number, CreateIssueCommentOption().body(body)))

    suspend fun setIssueState(number: Long, closed: Boolean): Issue {
        val option = EditIssueOption().apply { state = if (closed) "closed" else "open" }
        return call(api.getIssueApi().issueEditIssue(owner, repo, number, option))
    }

    /**
     * Sets the branch or tag shown as reference in the issue sidebar; the branch may not be pushed yet.
     */
    suspend fun setIssueRef(number: Long, ref: String): Issue {
        val option = EditIssueOption().apply { this.ref = ref }
        return call(api.getIssueApi().issueEditIssue(owner, repo, number, option))
    }

    suspend fun createIssue(option: CreateIssueOption): Issue =
        call(api.getIssueApi().issueCreateIssue(owner, repo, option))

    suspend fun listLabels(): List<Label> = call(api.getIssueApi().issueListLabels(owner, repo, 1, 50))

    suspend fun listMilestones(): List<Milestone> =
        call(api.getIssueApi().issueGetMilestonesList(owner, repo, "open", null, 1, 50))

    suspend fun listPullRequests(filter: GiteaPullRequestFilter, page: Int, limit: Int): List<PullRequest> {
        val poster = if (filter.createdByMe) getCurrentUser().login else null
        return call(
            api.getRepoApi().repoListPullRequests(
                owner, repo, null, filter.state.apiValue, "recentupdate", null, null, poster, page, limit
            )
        )
    }

    suspend fun getPullRequest(number: Long): PullRequest = call(api.getRepoApi().repoGetPullRequest(owner, repo, number))

    suspend fun getPullRequestCommits(number: Long): List<Commit> =
        fetchAllPages { page -> api.getRepoApi().repoGetPullRequestCommits(owner, repo, number, page, PAGE_LIMIT, false, false) }

    suspend fun listReviews(number: Long): List<PullReview> =
        fetchAllPages { page -> api.getRepoApi().repoListPullReviews(owner, repo, number, page, PAGE_LIMIT) }

    /**
     * @return null when the commit has no status (no CI configured)
     */
    suspend fun getCombinedStatus(sha: String): CombinedStatus? =
        call(api.getRepoApi().repoGetCombinedStatusByRef(owner, repo, sha, 1, PAGE_LIMIT)).takeIf { !it.statuses.isNullOrEmpty() }

    suspend fun setPullRequestState(number: Long, closed: Boolean): PullRequest {
        val option = EditPullRequestOption().apply { state = if (closed) "closed" else "open" }
        return call(api.getRepoApi().repoEditPullRequest(owner, repo, number, option))
    }

    /**
     * @param body may be empty to approve, mandatory for the other events
     */
    suspend fun createReview(pr: PullRequest, event: CreatePullReviewOptions.EventEnum, body: String): PullReview {
        val option = CreatePullReviewOptions().apply {
            this.event = event
            this.body = body
            // the review applies to the commit the user has seen
            commitId = pr.head?.sha
        }
        return call(api.getRepoApi().repoCreatePullReview(option, owner, repo, pr.number))
    }

    /**
     * Merges [pr] only if its head is still the one shown to the user (head_commit_id), otherwise Gitea refuses.
     * Empty title and message let Gitea use its defaults.
     */
    suspend fun merge(pr: PullRequest, style: MergePullRequestOption.DoEnum, title: String, message: String, deleteBranch: Boolean) {
        val option = MergePullRequestOption().apply {
            `do` = style
            mergeTitleField = title.takeIf { it.isNotBlank() }
            mergeMessageField = message.takeIf { it.isNotBlank() }
            headCommitId = pr.head?.sha
            setDeleteBranchAfterMerge(deleteBranch)
        }
        callWithoutBody(api.getRepoApi().repoMergePullRequest(owner, repo, pr.number, option))
    }

    /** Repository settings, e.g. the allowed merge styles. */
    suspend fun getRepository(): Repository = call(api.getRepoApi().repoGet(owner, repo))

    /** Pages of [PAGE_LIMIT] items until a short page, at most [MAX_PAGES]. */
    private suspend fun <T> fetchAllPages(request: (Int) -> Call<List<T>>): List<T> {
        val result = mutableListOf<T>()
        for (page in 1..MAX_PAGES) {
            val items = call(request(page))
            result.addAll(items)
            if (items.size < PAGE_LIMIT) break
        }
        return result
    }

    /**
     * Renders markdown as the web UI does for comments: issue references and mentions become links.
     */
    suspend fun renderMarkdown(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val body = call(api.getMarkdownApi().render(GiteaMarkdownApi.options(text, "comment", "$owner/$repo")))
        return withContext(Dispatchers.IO) { body.use { it.string() } }
    }

    private suspend fun <T> call(call: Call<T>): T {
        val response = call.awaitResponse()
        if (!response.isSuccessful) {
            throw GiteaApiException(errorMessage(response), response.code())
        }
        return response.body() ?: throw GiteaApiException("Empty response (HTTP ${response.code()})", response.code())
    }

    private suspend fun callWithoutBody(call: Call<Void>) {
        val response = call.awaitResponse()
        if (!response.isSuccessful) {
            throw GiteaApiException(errorMessage(response), response.code())
        }
    }

    private suspend fun errorMessage(response: Response<*>): String {
        val raw = withContext(Dispatchers.IO) { response.errorBody()?.use { it.string() } }.orEmpty()
        // Gitea errors look like {"message": "...", "url": "..."}
        val message = runCatching { JsonParser.parseString(raw).asJsonObject.get("message")?.asString }.getOrNull()
        return "HTTP ${response.code()}: ${message ?: response.message().ifEmpty { raw.take(200) }}"
    }

    companion object {
        /** Maximum page size accepted by Gitea by default (MAX_RESPONSE_ITEMS) */
        private const val PAGE_LIMIT = 50
        private const val MAX_PAGES = 10

        /**
         * @return null when no token is stored for the account
         */
        suspend fun create(mapping: GiteaRepositoryMapping): GiteaRepositoryClient? {
            val token = service<GiteaAccountManager>().findCredentials(mapping.account) ?: return null
            return GiteaRepositoryClient(GiteaSettings.getInstance().getGiteaApi(mapping.account.server.toString(), token), mapping.path)
        }
    }
}
