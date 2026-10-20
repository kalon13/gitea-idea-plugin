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
)
