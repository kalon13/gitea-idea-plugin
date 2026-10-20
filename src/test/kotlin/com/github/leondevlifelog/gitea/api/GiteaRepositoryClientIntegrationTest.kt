/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import com.github.leondevlifelog.gitea.services.GiteaSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gitnex.tea4j.v2.models.CreateIssueOption
import org.gitnex.tea4j.v2.models.Issue

/**
 * Runs against a real Gitea instance, skipped unless these variables are set:
 * - GITEA_TEST_URL, e.g. http://127.0.0.1:3000
 * - GITEA_TEST_TOKEN, token with read/write access to issues and read access to the user
 * - GITEA_TEST_REPO, owner/repo of a disposable repository: the test creates and closes issues
 */
class GiteaRepositoryClientIntegrationTest : BasePlatformTestCase() {

    private val url = System.getenv("GITEA_TEST_URL")
    private val token = System.getenv("GITEA_TEST_TOKEN")
    private val repo = System.getenv("GITEA_TEST_REPO")

    private fun client(): GiteaRepositoryClient? {
        if (url.isNullOrBlank() || token.isNullOrBlank() || repo.isNullOrBlank()) return null
        val (owner, name) = repo.split('/', limit = 2)
        return GiteaRepositoryClient(GiteaSettings.getInstance().getGiteaApi(url, token), GiteaRepositoryPath(owner, name))
    }

    fun testIssueLifecycle() = runBlocking {
        val client = client() ?: return@runBlocking
        val login = client.getCurrentUser().login

        val title = "integration test ${System.currentTimeMillis()}"
        val created = client.createIssue(CreateIssueOption().apply {
            this.title = title
            body = "body **bold**"
            assignees = listOf(login)
        })
        try {
            assertEquals(Issue.StateEnum.OPEN, created.state)
            // newest first: the new issue is on the first page
            assertTrue(client.listIssues(GiteaIssueFilter(), 1, 30).any { it.number == created.number })
            assertTrue(client.listIssues(GiteaIssueFilter(assignedToMe = true), 1, 30).any { it.number == created.number })
            assertTrue(client.listIssues(GiteaIssueFilter(createdByMe = true), 1, 30).any { it.number == created.number })

            assertEquals("issue-${created.number}-not-pushed", client.setIssueRef(created.number, "issue-${created.number}-not-pushed").ref)
            assertEquals("issue-${created.number}-not-pushed", client.getIssue(created.number).ref)

            client.addComment(created.number, "a **comment**")
            assertEquals(listOf("a **comment**"), client.getComments(created.number).map { it.body })

            assertEquals(Issue.StateEnum.CLOSED, client.setIssueState(created.number, true).state)
            assertFalse(client.listIssues(GiteaIssueFilter(), 1, 30).any { it.number == created.number })
            val closed = client.listIssues(GiteaIssueFilter(state = GiteaIssueState.CLOSED), 1, 30)
            assertTrue(closed.any { it.number == created.number })

            assertEquals(Issue.StateEnum.OPEN, client.setIssueState(created.number, false).state)
        } finally {
            client.setIssueState(created.number, true)
        }
    }

    fun testSearch() = runBlocking {
        val client = client() ?: return@runBlocking
        val word = "needle${System.currentTimeMillis()}"
        val created = client.createIssue(CreateIssueOption().apply { title = "search $word" })
        try {
            // the issue indexer is asynchronous: wait until the new issue is searchable
            var found = emptyList<Issue>()
            for (attempt in 1..20) {
                found = client.listIssues(GiteaIssueFilter(query = word), 1, 30)
                if (found.isNotEmpty()) break
                delay(500)
            }
            assertEquals(listOf(created.number), found.map { it.number })
        } finally {
            client.setIssueState(created.number, true)
        }
    }

    fun testSearchByNumber() = runBlocking {
        val client = client() ?: return@runBlocking
        val created = client.createIssue(CreateIssueOption().apply { title = "by number" })
        try {
            client.setIssueState(created.number, true)
            // direct lookup: other criteria (here the open state) are ignored
            val found = client.listIssues(GiteaIssueFilter(query = "#${created.number}"), 1, 30)
            assertEquals(listOf(created.number), found.map { it.number })
            assertTrue(client.listIssues(GiteaIssueFilter(query = "#${created.number}"), 2, 30).isEmpty())
            assertTrue(client.listIssues(GiteaIssueFilter(query = "#999999"), 1, 30).isEmpty())
        } finally {
            client.setIssueState(created.number, true)
        }
    }

    fun testListIssuesExcludesPullRequests() = runBlocking {
        val client = client() ?: return@runBlocking
        val issues = client.listIssues(GiteaIssueFilter(state = GiteaIssueState.ALL), 1, 50)
        assertTrue(issues.all { it.pullRequest == null })
    }

    fun testRenderMarkdown() = runBlocking {
        val client = client() ?: return@runBlocking
        val html = client.renderMarkdown("some **bold** text")
        assertTrue(html, html.contains("<strong>bold</strong>"))
        assertEquals("", client.renderMarkdown("  "))
    }

    fun testErrorMessage() = runBlocking {
        val client = client() ?: return@runBlocking
        try {
            client.getIssue(Long.MAX_VALUE / 2)
            fail("expected GiteaApiException")
        } catch (e: GiteaApiException) {
            assertEquals(404, e.statusCode)
            assertTrue(e.message!!, e.message!!.startsWith("HTTP 404"))
        }
    }
}
