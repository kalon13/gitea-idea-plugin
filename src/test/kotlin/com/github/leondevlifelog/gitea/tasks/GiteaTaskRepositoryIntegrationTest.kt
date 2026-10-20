/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.tasks

import com.intellij.tasks.CustomTaskState
import com.intellij.tasks.impl.LocalTaskImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Task server against a real Gitea instance; same variables as GiteaRepositoryClientIntegrationTest.
 */
class GiteaTaskRepositoryIntegrationTest : BasePlatformTestCase() {

    private fun repository(): GiteaRepository? {
        val url = System.getenv("GITEA_TEST_URL")
        val token = System.getenv("GITEA_TEST_TOKEN")
        val repo = System.getenv("GITEA_TEST_REPO")
        if (url.isNullOrBlank() || token.isNullOrBlank() || repo.isNullOrBlank()) return null
        return GiteaRepository(GiteaRepositoryType()).apply {
            this.url = url
            password = token
            repoAuthor = repo.substringBefore('/')
            repoName = repo.substringAfter('/')
        }
    }

    fun testConnection() {
        val repository = repository() ?: return
        assertNull(repository.createCancellableConnection()!!.call())
    }

    fun testIssuesAndFindTask() {
        val repository = repository() ?: return
        val issues = repository.getIssues(null, 0, 30, true)
        assertTrue(issues.isNotEmpty())
        val first = issues.first()
        assertEquals(first.id, repository.findTask(first.id)!!.id)
        assertEquals(first.id, repository.findTask("#${first.id}")!!.id)
        assertNull(repository.findTask("999999"))
    }

    fun testAssignedOnlyAndOpenOnly() {
        val repository = repository() ?: return
        val all = repository.getIssues(null, 0, 50, true)
        assertTrue(repository.getIssues(null, 0, 50, false).none { it.isClosed })
        repository.assigned = true
        val assigned = repository.getIssues(null, 0, 50, true)
        assertTrue(assigned.size <= all.size)
    }

    fun testStateAndTimeTracking() {
        val repository = repository() ?: return
        val task = repository.getIssues(null, 0, 30, false).firstOrNull() ?: return
        repository.updateTimeSpent(LocalTaskImpl(task), "1h 5m", "")
        repository.setTaskState(task, CustomTaskState("closed", "Close"))
        assertTrue(repository.findTask(task.id)!!.isClosed)
        repository.setTaskState(task, CustomTaskState("open", "Open"))
        assertFalse(repository.findTask(task.id)!!.isClosed)
    }
}
