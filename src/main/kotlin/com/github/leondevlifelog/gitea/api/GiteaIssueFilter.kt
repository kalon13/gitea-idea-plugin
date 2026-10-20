/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

enum class GiteaIssueState(val apiValue: String) {
    OPEN("open"), CLOSED("closed"), ALL("all")
}

/**
 * Search criteria of the issue list.
 * @param label label name, null for any
 * @param milestone milestone title, null for any
 */
data class GiteaIssueFilter(
    val state: GiteaIssueState = GiteaIssueState.OPEN,
    val query: String = "",
    val label: String? = null,
    val milestone: String? = null,
    val assignedToMe: Boolean = false,
    val createdByMe: Boolean = false,
) {
    /**
     * Number of the issue when the query is `#12`: the issue is opened directly, the other criteria are ignored.
     * Gitea's search finds nothing for `#12`; a plain `12` is left to it, since it also matches the number in texts.
     */
    val issueNumber: Long?
        get() = ISSUE_NUMBER.matchEntire(query.trim())?.groupValues?.get(1)?.toLongOrNull()

    private companion object {
        val ISSUE_NUMBER = Regex("""#(\d+)""")
    }
}
