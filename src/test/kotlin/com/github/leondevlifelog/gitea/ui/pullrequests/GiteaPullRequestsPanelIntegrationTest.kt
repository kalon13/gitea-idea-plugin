/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.api.GiteaTestServer
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssuesPanelIntegrationTest
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.gitnex.tea4j.v2.models.Commit
import org.gitnex.tea4j.v2.models.PullRequest
import javax.swing.JEditorPane

/**
 * Drives the real pull requests panel against a Gitea instance, see [GiteaTestServer].
 */
class GiteaPullRequestsPanelIntegrationTest : BasePlatformTestCase() {

    fun testListAndDetails() {
        if (!GiteaTestServer.isConfigured) return
        val created = GiteaTestServer.createPullRequest("panel ${System.currentTimeMillis()}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
        try {
            val panel = GiteaPullRequestsPanel(
                project, GiteaIssuesPanelIntegrationTest.fakeMapping(), GiteaTestServer.client(), scope
            )
            // JBList wraps its renderer: the lists are told apart by position, the pull requests come first
            @Suppress("UNCHECKED_CAST")
            val list = UIUtil.findComponentsOfType(panel, JBList::class.java).first() as JBList<PullRequest>
            waitUntil("pull requests loaded") { list.model.size > 0 }
            val index = (0 until list.model.size).first { list.model.getElementAt(it).number == created.number }
            list.selectedIndex = index

            val pane = UIUtil.findComponentOfType(panel, JEditorPane::class.java)!!
            waitUntil("overview rendered") { pane.text.contains("<strong>body</strong>") }
            assertTrue(pane.text, pane.text.contains("wants to merge"))

            UIUtil.findComponentOfType(panel, JBTabbedPane::class.java)!!.selectedIndex = 1
            @Suppress("UNCHECKED_CAST")
            val commits = UIUtil.findComponentsOfType(panel, JBList::class.java)
                .first { it !== list } as JBList<Commit>
            waitUntil("commits loaded") { commits.model.size == 1 }
        } finally {
            scope.cancel()
            runBlocking { GiteaTestServer.client().setPullRequestState(created.number, true) }
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) =
        GiteaIssuesPanelIntegrationTest.waitUntil(what, condition)
}
