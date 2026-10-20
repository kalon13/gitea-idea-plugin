/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import junit.framework.TestCase

class GiteaMarkdownApiTest : TestCase() {

    fun testOptionsHaveBothCasings() {
        val options = GiteaMarkdownApi.options("**x**", "comment", "owner/repo")
        assertEquals("**x**", options["text"])
        assertEquals("**x**", options["Text"])
        assertEquals("comment", options["Mode"])
        assertEquals("owner/repo", options["Context"])
    }
}
