/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import junit.framework.TestCase
import org.gitnex.tea4j.v2.models.MergePullRequestOption.DoEnum
import org.gitnex.tea4j.v2.models.Repository

class GiteaMergeDialogTest : TestCase() {

    private fun repository(default: String?, vararg allowed: DoEnum) = Repository().apply {
        setAllowMergeCommits(DoEnum.MERGE in allowed)
        setAllowRebase(DoEnum.REBASE in allowed)
        setAllowRebaseExplicit(DoEnum.REBASE_MERGE in allowed)
        setAllowSquashMerge(DoEnum.SQUASH in allowed)
        setAllowFastForwardOnlyMerge(DoEnum.FAST_FORWARD_ONLY in allowed)
        defaultMergeStyle = default
    }

    fun testAllowedStylesInWebOrder() {
        val repository = repository("squash", DoEnum.SQUASH, DoEnum.MERGE, DoEnum.FAST_FORWARD_ONLY)
        assertEquals(listOf(DoEnum.MERGE, DoEnum.SQUASH, DoEnum.FAST_FORWARD_ONLY), GiteaMergeDialog.allowedStyles(repository))
        assertEquals(DoEnum.SQUASH, GiteaMergeDialog.defaultStyle(repository))
    }

    fun testDefaultNotAllowedFallsBackToFirst() {
        assertEquals(DoEnum.REBASE, GiteaMergeDialog.defaultStyle(repository("merge", DoEnum.REBASE, DoEnum.SQUASH)))
        assertEquals(DoEnum.REBASE, GiteaMergeDialog.defaultStyle(repository(null, DoEnum.REBASE)))
    }

    fun testRebaseMergeValue() {
        assertEquals(DoEnum.REBASE_MERGE, GiteaMergeDialog.defaultStyle(repository("rebase-merge", DoEnum.REBASE_MERGE)))
    }
}
