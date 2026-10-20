/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.util

import junit.framework.TestCase

class GiteaUrlUtilTest : TestCase() {

    private fun path(url: String) = GiteaUrlUtil.getUserAndRepositoryFromRemoteUrl(url)?.toString()

    fun testHttpUrl() {
        assertEquals("user/repo", path("https://gitea.example.com/user/repo.git"))
    }

    fun testHttpUrlWithSubPath() {
        assertEquals("user/repo", path("https://example.com/gitea/user/repo"))
    }

    fun testScpLikeUrl() {
        assertEquals("user/repo", path("git@gitea.example.com:user/repo.git"))
    }

    fun testSshUrlWithPort() {
        assertEquals("user/repo", path("ssh://git@gitea.example.com:2222/user/repo.git"))
    }

    fun testTrailingSlash() {
        assertEquals("user/repo", path("https://gitea.example.com/user/repo/"))
    }

    fun testInvalidUrl() {
        assertNull(path("repo"))
    }
}
