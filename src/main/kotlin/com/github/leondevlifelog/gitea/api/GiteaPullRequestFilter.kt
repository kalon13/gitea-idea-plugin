/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

/**
 * Search criteria of the pull request list. The API has no text search for pull requests.
 */
data class GiteaPullRequestFilter(
    val state: GiteaIssueState = GiteaIssueState.OPEN,
    val createdByMe: Boolean = false,
)
