/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import junit.framework.TestCase

class GiteaIssueFilterTest : TestCase() {

    fun testIssueNumber() {
        assertEquals(12L, GiteaIssueFilter(query = "#12").issueNumber)
        assertEquals(12L, GiteaIssueFilter(query = "  #12 ").issueNumber)
    }

    fun testNotAnIssueNumber() {
        assertNull(GiteaIssueFilter(query = "12").issueNumber)
        assertNull(GiteaIssueFilter(query = "#12 login").issueNumber)
        assertNull(GiteaIssueFilter(query = "#").issueNumber)
        assertNull(GiteaIssueFilter(query = "").issueNumber)
    }
}
