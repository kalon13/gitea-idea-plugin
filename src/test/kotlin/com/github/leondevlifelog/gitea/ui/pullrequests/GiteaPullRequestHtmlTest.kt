/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.gitnex.tea4j.v2.models.PRBranchInfo
import org.gitnex.tea4j.v2.models.PullRequest
import org.gitnex.tea4j.v2.models.PullReview
import org.gitnex.tea4j.v2.models.User
import java.util.*

class GiteaPullRequestHtmlTest : BasePlatformTestCase() {

    private fun review(login: String, state: PullReview.StateEnum, minute: Int, dismissed: Boolean = false) =
        PullReview().apply {
            user = User().apply { this.login = login }
            this.state = state
            submittedAt = Date(minute * 60_000L)
            setDismissed(dismissed)
        }

    fun testLatestReviewKeepsVerdictAfterComment() {
        val latest = GiteaPullRequestHtml.latestReviews(listOf(
            review("a", PullReview.StateEnum.REQUEST_CHANGES, 1),
            review("a", PullReview.StateEnum.APPROVED, 2),
            review("a", PullReview.StateEnum.COMMENT, 3),
            review("b", PullReview.StateEnum.COMMENT, 1),
            review("c", PullReview.StateEnum.APPROVED, 1, dismissed = true),
        ))
        assertEquals(PullReview.StateEnum.APPROVED, latest["a"]!!.state)
        assertEquals(PullReview.StateEnum.COMMENT, latest["b"]!!.state)
        assertNull(latest["c"])
    }

    fun testStates() {
        val pr = PullRequest().apply { state = PullRequest.StateEnum.OPEN }
        assertEquals(GiteaPullRequestState.OPEN, GiteaPullRequestState.of(pr))
        pr.setDraft(true)
        assertEquals(GiteaPullRequestState.DRAFT, GiteaPullRequestState.of(pr))
        pr.state = PullRequest.StateEnum.CLOSED
        assertEquals(GiteaPullRequestState.CLOSED, GiteaPullRequestState.of(pr))
        pr.setMerged(true)
        assertEquals(GiteaPullRequestState.MERGED, GiteaPullRequestState.of(pr))
    }

    fun testOverview() {
        val pr = PullRequest().apply {
            number = 3
            title = "Add <feature>"
            state = PullRequest.StateEnum.OPEN
            setMergeable(false)
            user = User().apply { login = "tester" }
            head = PRBranchInfo().apply { ref = "feature"; label = "feature" }
            base = PRBranchInfo().apply { ref = "main" }
            requestedReviewers = listOf(User().apply { login = "other" })
        }
        val html = GiteaPullRequestHtml.build(pr, "<p>body</p>", emptyList(), emptyList(), null)
        assertTrue(html, html.contains("Add &lt;feature&gt;"))
        assertTrue(html, html.contains("tester wants to merge feature into main"))
        assertTrue(html, html.contains("cannot be merged automatically"))
        assertTrue(html, html.contains("other"))
        assertTrue(html, html.contains("review requested"))
    }
}
