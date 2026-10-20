/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssueListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.gitnex.tea4j.v2.models.PullRequest
import java.awt.Color

/**
 * State of a pull request as shown to the user: Gitea reports merged pull requests as closed.
 */
enum class GiteaPullRequestState(private val key: String) {
    DRAFT("pull.state.draft"), OPEN("pull.state.open"), MERGED("pull.state.merged"), CLOSED("pull.state.closed");

    val text: String get() = GiteaBundle.message(key)

    val color: Color
        get() = when (this) {
            DRAFT -> UIUtil.getContextHelpForeground()
            OPEN -> GiteaIssueListCellRenderer.OPEN_COLOR
            MERGED -> GiteaIssueListCellRenderer.CLOSED_COLOR
            CLOSED -> CLOSED_COLOR
        }

    companion object {
        private val CLOSED_COLOR: Color = JBColor(Color(0xCF222E), Color(0xF85149))

        fun of(pullRequest: PullRequest): GiteaPullRequestState = when {
            pullRequest.isMerged == true -> MERGED
            pullRequest.state == PullRequest.StateEnum.CLOSED -> CLOSED
            pullRequest.isDraft == true -> DRAFT
            else -> OPEN
        }
    }
}
