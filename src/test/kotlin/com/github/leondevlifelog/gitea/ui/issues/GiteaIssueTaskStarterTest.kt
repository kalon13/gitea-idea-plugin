/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import junit.framework.TestCase

class GiteaIssueTaskStarterTest : TestCase() {

    fun testBranchName() {
        assertEquals("issue-12-fix-login-page", GiteaIssueTaskStarter.branchName(12, "Fix login page"))
    }

    fun testBranchNameStripsSymbolsAndAccents() {
        assertEquals("issue-3-crash-on-save-perch", GiteaIssueTaskStarter.branchName(3, "  Crash on *save*: perché?! "))
    }

    fun testBranchNameIsShortened() {
        val name = GiteaIssueTaskStarter.branchName(1, "a very long title that goes on and on beyond any reasonable size")
        assertEquals("issue-1-a-very-long-title-that-goes-on-and-on-be", name)
        assertFalse(name.endsWith("-"))
    }

    fun testBranchNameWithoutUsableTitle() {
        assertEquals("issue-7", GiteaIssueTaskStarter.branchName(7, "???"))
        assertEquals("issue-7", GiteaIssueTaskStarter.branchName(7, null))
    }
}
