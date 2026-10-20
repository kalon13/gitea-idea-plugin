/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.gitnex.tea4j.v2.models.Issue
import org.gitnex.tea4j.v2.models.User

class GiteaIssueHtmlTest : BasePlatformTestCase() {

    fun testImagesBecomeLinks() {
        val html = GiteaIssueHtml.sanitize("""<p>see <img alt="x" src="http://host/attachments/1"> here</p>""")
        assertEquals("""<p>see <a href="http://host/attachments/1">[image]</a> here</p>""", html)
    }

    fun testTitleIsEscaped() {
        val issue = Issue().apply {
            number = 5
            title = "<script>x</script> & co"
            state = Issue.StateEnum.OPEN
            user = User().apply { login = "tester" }
        }
        val html = GiteaIssueHtml.build(issue, "<p>body</p>", emptyList())
        assertTrue(html, html.contains("&lt;script&gt;x&lt;/script&gt; &amp; co"))
        assertTrue(html, html.contains("<p>body</p>"))
        assertFalse(html, html.contains("Branch:"))
    }

    fun testBranchReference() {
        val issue = Issue().apply {
            number = 5
            title = "x"
            state = Issue.StateEnum.OPEN
            ref = "issue-5-x"
        }
        assertTrue(GiteaIssueHtml.build(issue, "", emptyList()).contains("Branch: issue-5-x"))
    }
}
