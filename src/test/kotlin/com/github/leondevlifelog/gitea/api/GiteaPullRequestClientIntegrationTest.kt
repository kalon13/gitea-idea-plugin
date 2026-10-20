/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gitnex.tea4j.v2.models.CombinedStatus
import org.gitnex.tea4j.v2.models.CreatePullReviewOptions
import org.gitnex.tea4j.v2.models.CreateStatusOption
import org.gitnex.tea4j.v2.models.MergePullRequestOption
import org.gitnex.tea4j.v2.models.PullRequest
import org.gitnex.tea4j.v2.models.PullReview

/**
 * Pull request API against a real Gitea instance, see [GiteaTestServer].
 */
class GiteaPullRequestClientIntegrationTest : BasePlatformTestCase() {

    fun testListDetailsAndState() = runBlocking {
        if (!GiteaTestServer.isConfigured) return@runBlocking
        val client = GiteaTestServer.client()
        val created = GiteaTestServer.createPullRequest("pull request ${System.currentTimeMillis()}")
        try {
            assertTrue(client.listPullRequests(GiteaPullRequestFilter(), 1, 30).any { it.number == created.number })
            assertTrue(
                client.listPullRequests(GiteaPullRequestFilter(createdByMe = true), 1, 30).any { it.number == created.number }
            )

            val pr = client.getPullRequest(created.number)
            assertEquals(created.head.ref, pr.head.ref)
            assertEquals(1, client.getPullRequestCommits(created.number).size)
            assertNull(client.getCombinedStatus(pr.head.sha))

            GiteaTestServer.execute(
                GiteaTestServer.api().getRepoApi().repoCreateStatus(
                    GiteaTestServer.path.owner, GiteaTestServer.path.repository, pr.head.sha,
                    CreateStatusOption().apply {
                        context = "ci/test"
                        state = CreateStatusOption.StateEnum.SUCCESS
                        description = "all green"
                    })
            )
            val status = client.getCombinedStatus(pr.head.sha)!!
            assertEquals(CombinedStatus.StateEnum.SUCCESS, status.state)
            assertEquals(listOf("ci/test"), status.statuses.map { it.context })

            assertEquals(PullRequest.StateEnum.CLOSED, client.setPullRequestState(created.number, true).state)
            assertTrue(
                client.listPullRequests(GiteaPullRequestFilter(state = GiteaIssueState.CLOSED), 1, 30)
                    .any { it.number == created.number }
            )
            assertEquals(PullRequest.StateEnum.OPEN, client.setPullRequestState(created.number, false).state)

            client.addComment(created.number, "a comment")
            assertEquals(listOf("a comment"), client.getComments(created.number).map { it.body })
        } finally {
            client.setPullRequestState(created.number, true)
        }
    }

    fun testReviewRules() = runBlocking {
        if (!GiteaTestServer.isConfigured) return@runBlocking
        val otherToken = GiteaTestServer.otherToken ?: return@runBlocking
        val client = GiteaTestServer.client()
        val other = GiteaTestServer.client(otherToken)
        val created = GiteaTestServer.createPullRequest("review rules ${System.currentTimeMillis()}")
        try {
            // the rules enforced by GiteaReviewDialog
            assertEquals(422, assertApiError { client.createReview(created, CreatePullReviewOptions.EventEnum.APPROVED, "") })
            assertEquals(422, assertApiError { other.createReview(created, CreatePullReviewOptions.EventEnum.COMMENT, "") })
            client.createReview(created, CreatePullReviewOptions.EventEnum.COMMENT, "own comment")
            other.createReview(created, CreatePullReviewOptions.EventEnum.REQUEST_CHANGES, "please fix")
            other.createReview(created, CreatePullReviewOptions.EventEnum.APPROVED, "")

            val states = client.listReviews(created.number).map { it.user.login to it.state }
            assertTrue(states.toString(), states.containsAll(listOf(
                "tester" to PullReview.StateEnum.COMMENT,
                "other" to PullReview.StateEnum.REQUEST_CHANGES,
                "other" to PullReview.StateEnum.APPROVED,
            )))
        } finally {
            client.setPullRequestState(created.number, true)
        }
    }

    fun testSquashMergeDeletingBranch() = runBlocking {
        if (!GiteaTestServer.isConfigured) return@runBlocking
        val client = GiteaTestServer.client()
        val created = GiteaTestServer.createPullRequest("squash ${System.currentTimeMillis()}")
        waitMergeable(client, created.number)
        client.merge(created, MergePullRequestOption.DoEnum.SQUASH, "Squashed title", "Squashed body", true)

        val merged = client.getPullRequest(created.number)
        assertTrue(merged.isMerged)
        assertFalse(GiteaTestServer.branchExists(created.head.ref))
        val message = GiteaTestServer.execute(
            GiteaTestServer.api().getRepoApi().repoGetSingleCommit(
                GiteaTestServer.path.owner, GiteaTestServer.path.repository, merged.mergeCommitSha, false, false, false
            )
        ).commit.message
        assertEquals("Squashed title\n\nSquashed body\n", message)
    }

    fun testMergeRefusedWhenHeadChanged() = runBlocking {
        if (!GiteaTestServer.isConfigured) return@runBlocking
        val client = GiteaTestServer.client()
        val created = GiteaTestServer.createPullRequest("moving head ${System.currentTimeMillis()}")
        try {
            GiteaTestServer.addCommit(created.head.ref)
            waitMergeable(client, created.number)
            // created still has the old head: the user did not see the new commit
            assertApiError { client.merge(created, MergePullRequestOption.DoEnum.MERGE, "", "", false) }
            assertFalse(client.getPullRequest(created.number).isMerged)
        } finally {
            client.setPullRequestState(created.number, true)
        }
    }

    fun testRepositorySettings() = runBlocking {
        if (!GiteaTestServer.isConfigured) return@runBlocking
        val repository = GiteaTestServer.client().getRepository()
        assertEquals(GiteaTestServer.defaultBranch, repository.defaultBranch)
        assertNotNull(repository.defaultMergeStyle)
    }

    /** Gitea checks the mergeability in background after a push. */
    private suspend fun waitMergeable(client: GiteaRepositoryClient, number: Long) {
        for (attempt in 1..30) {
            if (client.getPullRequest(number).isMergeable == true) return
            delay(300)
        }
        fail("pull request #$number not mergeable")
    }

    private suspend fun assertApiError(action: suspend () -> Unit): Int {
        try {
            action()
        } catch (e: GiteaApiException) {
            return e.statusCode
        }
        fail("expected GiteaApiException")
        return 0
    }

    fun testReviewsOfAnotherUser() = runBlocking {
        if (!GiteaTestServer.isConfigured) return@runBlocking
        val otherToken = GiteaTestServer.otherToken ?: return@runBlocking
        val client = GiteaTestServer.client()
        val created = GiteaTestServer.createPullRequest("reviewed ${System.currentTimeMillis()}")
        try {
            val review = CreatePullReviewOptions().apply {
                event = CreatePullReviewOptions.EventEnum.APPROVED
                body = "looks good"
            }
            GiteaTestServer.execute(
                GiteaTestServer.api(otherToken).getRepoApi()
                    .repoCreatePullReview(review, GiteaTestServer.path.owner, GiteaTestServer.path.repository, created.number)
            )
            val reviews = client.listReviews(created.number)
            assertTrue(reviews.any { it.state == PullReview.StateEnum.APPROVED && it.body == "looks good" })
        } finally {
            client.setPullRequestState(created.number, true)
        }
    }
}
