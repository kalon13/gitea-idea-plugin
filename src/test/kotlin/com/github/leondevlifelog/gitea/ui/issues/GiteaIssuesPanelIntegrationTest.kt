/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.api.GiteaIssueState
import com.github.leondevlifelog.gitea.api.GiteaRepositoryClient
import com.github.leondevlifelog.gitea.api.GiteaRepositoryPath
import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccount
import com.github.leondevlifelog.gitea.authentication.accounts.GiteaServerPath
import com.github.leondevlifelog.gitea.services.GiteaRepositoryMapping
import com.github.leondevlifelog.gitea.services.GiteaSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.gitnex.tea4j.v2.models.Issue
import java.lang.reflect.Proxy
import javax.swing.JEditorPane

/**
 * Drives the real issues panel against a Gitea instance; same variables as GiteaRepositoryClientIntegrationTest.
 * The repository needs at least one open and one closed issue.
 */
class GiteaIssuesPanelIntegrationTest : BasePlatformTestCase() {

    fun testListDetailsAndFilter() {
        val url = System.getenv("GITEA_TEST_URL")
        val token = System.getenv("GITEA_TEST_TOKEN")
        val repo = System.getenv("GITEA_TEST_REPO")
        if (url.isNullOrBlank() || token.isNullOrBlank() || repo.isNullOrBlank()) return

        val path = GiteaRepositoryPath(repo.substringBefore('/'), repo.substringAfter('/'))
        val mapping = GiteaRepositoryMapping(
            GiteaAccount("tester", GiteaServerPath.from(url)), path, fakeGitRepository(),
            GitRemote(GitRemote.ORIGIN, listOf("$url/$repo.git"), emptyList(), emptyList(), emptyList())
        )
        val client = GiteaRepositoryClient(GiteaSettings.getInstance().getGiteaApi(url, token), path)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
        try {
            val panel = GiteaIssuesPanel(project, mapping, client, scope)
            @Suppress("UNCHECKED_CAST")
            val list = UIUtil.findComponentOfType(panel, JBList::class.java) as JBList<Issue>

            waitUntil("open issues loaded") { list.model.size > 0 }
            val open = (0 until list.model.size).map { list.model.getElementAt(it) }
            assertTrue(open.none { it.isClosed() })

            list.selectedIndex = 0
            val selected = list.selectedValue
            val pane = UIUtil.findComponentOfType(panel, JEditorPane::class.java)!!
            waitUntil("details rendered") { !pane.text.contains("Loading...") && pane.text.contains("#${selected.number}") }
            assertTrue(pane.text, pane.text.contains(selected.title.substringBefore(' ')))

            val stateCombo = UIUtil.findComponentsOfType(panel, ComboBox::class.java).first { it.getItemAt(0) is GiteaIssueState }
            stateCombo.selectedItem = GiteaIssueState.CLOSED
            waitUntil("closed issues loaded") {
                list.model.size > 0 && (0 until list.model.size).all { list.model.getElementAt(it).isClosed() }
            }
        } finally {
            scope.cancel()
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("timeout waiting for: $what")
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(20)
        }
    }

    private fun fakeGitRepository(): GitRepository = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(GitRepository::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> "fake git repository"
            else -> throw UnsupportedOperationException(method.name)
        }
    } as GitRepository
}
