/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.services

import junit.framework.TestCase

class GiteaProtectedBranchPatternsTest : TestCase() {

    private fun matches(rule: String, branch: String) =
        branch.matches(Regex(GiteaProtectedBranchPatterns.globToRegex(rule)))

    fun testPlainName() {
        assertTrue(matches("main", "main"))
        assertFalse(matches("main", "main2"))
    }

    fun testSpecialCharsAreLiteral() {
        assertTrue(matches("release-1.0", "release-1.0"))
        assertFalse(matches("release-1.0", "release-1x0"))
    }

    fun testSingleStarDoesNotCrossSlash() {
        assertTrue(matches("release/*", "release/1.0"))
        assertFalse(matches("release/*", "release/1.0/hotfix"))
    }

    fun testDoubleStarCrossesSlash() {
        assertTrue(matches("release/**", "release/1.0/hotfix"))
    }

    fun testQuestionMark() {
        assertTrue(matches("v?", "v1"))
        assertFalse(matches("v?", "v10"))
    }
}
