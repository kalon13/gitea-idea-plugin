/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import junit.framework.TestCase

class GiteaPullRequestCreatorTest : TestCase() {

    fun testIssueNumberOfBranch() {
        assertEquals(12L, GiteaPullRequestCreator.issueNumber("issue-12-fix-login"))
        assertEquals(12L, GiteaPullRequestCreator.issueNumber("issue-12"))
        assertNull(GiteaPullRequestCreator.issueNumber("issue-12fix"))
        assertNull(GiteaPullRequestCreator.issueNumber("feature/issue-12"))
        assertNull(GiteaPullRequestCreator.issueNumber("main"))
    }

    fun testProposalForIssueBranch() {
        val proposal = GiteaPullRequestCreator.propose("issue-7-login", "Login is slow", "Speed up login")
        assertEquals("Login is slow", proposal.title)
        assertEquals("Closes #7", proposal.body)
    }

    fun testProposalForIssueBranchWithoutIssueTitle() {
        assertEquals("Speed up login", GiteaPullRequestCreator.propose("issue-7-login", null, "Speed up login").title)
        assertEquals("issue-7-login", GiteaPullRequestCreator.propose("issue-7-login", null, null).title)
    }

    fun testProposalForOtherBranch() {
        val proposal = GiteaPullRequestCreator.propose("feature/x", null, "Add x")
        assertEquals("Add x", proposal.title)
        assertEquals("", proposal.body)
        assertEquals("feature/x", GiteaPullRequestCreator.propose("feature/x", null, null).title)
    }
}
