/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.gitnex.tea4j.v2.models.CombinedStatus
import org.gitnex.tea4j.v2.models.CreatePullReviewOptions
import org.gitnex.tea4j.v2.models.CreateStatusOption
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
