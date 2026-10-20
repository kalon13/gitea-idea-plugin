/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.pullrequests

import com.github.leondevlifelog.gitea.GiteaBundle
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssueHtml
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssueHtml.escape
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssueHtml.formatDate
import com.github.leondevlifelog.gitea.ui.issues.GiteaIssueListCellRenderer
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.gitnex.tea4j.v2.models.CombinedStatus
import org.gitnex.tea4j.v2.models.CommitStatus
import org.gitnex.tea4j.v2.models.PullRequest
import org.gitnex.tea4j.v2.models.PullReview
import java.awt.Color

/**
 * Overview of a pull request: state, branches, mergeability, checks, reviews, description and comments.
 */
object GiteaPullRequestHtml {

    private val WARNING_COLOR: Color = JBColor(Color(0x9A6700), Color(0xD29922))
    private val ERROR_COLOR: Color = JBColor(Color(0xCF222E), Color(0xF85149))

    /**
     * @param bodyHtml null while loading; [comments], [reviews] and [status] are null when not loaded (yet)
     */
    fun build(
        pr: PullRequest,
        bodyHtml: String?,
        comments: List<GiteaIssueHtml.RenderedComment>?,
        reviews: List<PullReview>?,
        status: CombinedStatus?,
    ): String {
        val grey = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())
        val sb = StringBuilder("<html><body>")
        sb.append("<p><font size='+1'><b>").append(escape(pr.title))
            .append("</b> <font color='$grey'>#${pr.number}</font></font></p>")

        val state = GiteaPullRequestState.of(pr)
        sb.append("<p><b><font color='${ColorUtil.toHtmlColor(state.color)}'>").append(escape(state.text)).append("</font></b> ")
        val head = pr.head?.label ?: pr.head?.ref.orEmpty()
        val description = if (state == GiteaPullRequestState.MERGED) {
            GiteaBundle.message("pull.details.merged", pr.mergedBy?.login.orEmpty(), head, pr.base?.ref.orEmpty(), formatDate(pr.mergedAt))
        } else {
            GiteaBundle.message("pull.details.wants.merge", pr.user?.login.orEmpty(), head, pr.base?.ref.orEmpty())
        }
        sb.append("<font color='$grey'>").append(escape(description)).append("</font></p>")

        if (state == GiteaPullRequestState.OPEN || state == GiteaPullRequestState.DRAFT) {
            val (key, color) = if (pr.isMergeable == true) {
                "pull.details.mergeable" to GiteaIssueListCellRenderer.OPEN_COLOR
            } else {
                "pull.details.not.mergeable" to ERROR_COLOR
            }
            sb.append("<p><font color='${ColorUtil.toHtmlColor(color)}'>").append(escape(GiteaBundle.message(key)))
                .append("</font></p>")
        }

        status?.let { appendStatus(sb, it) }
        appendReviews(sb, pr, reviews.orEmpty())
        GiteaIssueHtml.appendLabels(sb, pr.labels.orEmpty())
        GiteaIssueHtml.appendBodyAndComments(sb, bodyHtml, comments)
        return sb.append("</body></html>").toString()
    }

    private fun appendStatus(sb: StringBuilder, status: CombinedStatus) {
        sb.append("<p><b>").append(escape(GiteaBundle.message("pull.details.checks"))).append("</b> ")
            .append(statusText(status.state?.value))
        status.statuses.orEmpty().forEach { appendCommitStatus(sb, it) }
        sb.append("</p>")
    }

    private fun appendCommitStatus(sb: StringBuilder, status: CommitStatus) {
        sb.append("<br>").append(statusText(status.status?.value)).append(" ")
        val context = escape(status.context)
        if (!status.targetUrl.isNullOrBlank()) {
            sb.append("<a href=\"").append(escape(status.targetUrl)).append("\">").append(context).append("</a>")
        } else {
            sb.append(context)
        }
        if (!status.description.isNullOrBlank()) {
            sb.append(" <font color='${ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())}'>")
                .append(escape(status.description)).append("</font>")
        }
    }

    private fun statusText(state: String?): String {
        val color = when (state) {
            "success" -> GiteaIssueListCellRenderer.OPEN_COLOR
            "failure", "error" -> ERROR_COLOR
            else -> WARNING_COLOR
        }
        return "<font color='${ColorUtil.toHtmlColor(color)}'>${escape(state.orEmpty())}</font>"
    }

    /**
     * Latest review of each reviewer (a later comment does not hide an approval), then the requested reviewers
     * who did not review yet.
     */
    private fun appendReviews(sb: StringBuilder, pr: PullRequest, reviews: List<PullReview>) {
        val byUser = latestReviews(reviews)
        val reviewed = byUser.keys
        val pending = pr.requestedReviewers.orEmpty().mapNotNull { it.login }.filter { it !in reviewed }
        if (byUser.isEmpty() && pending.isEmpty()) return

        sb.append("<p><b>").append(escape(GiteaBundle.message("pull.details.reviews"))).append("</b>")
        byUser.forEach { (login, review) ->
            val (key, color) = when (review.state) {
                PullReview.StateEnum.APPROVED -> "pull.review.approved" to GiteaIssueListCellRenderer.OPEN_COLOR
                PullReview.StateEnum.REQUEST_CHANGES -> "pull.review.changes" to ERROR_COLOR
                else -> "pull.review.commented" to UIUtil.getContextHelpForeground()
            }
            sb.append("<br>").append(escape(login)).append(" <font color='${ColorUtil.toHtmlColor(color)}'>")
                .append(escape(GiteaBundle.message(key))).append("</font>")
        }
        pending.forEach {
            sb.append("<br>").append(escape(it)).append(" <font color='${ColorUtil.toHtmlColor(WARNING_COLOR)}'>")
                .append(escape(GiteaBundle.message("pull.review.pending"))).append("</font>")
        }
        sb.append("</p>")
    }

    fun latestReviews(reviews: List<PullReview>): Map<String, PullReview> {
        val result = LinkedHashMap<String, PullReview>()
        reviews.filter { it.isDismissed != true && it.state != PullReview.StateEnum.PENDING }
            .sortedBy { it.submittedAt }
            .forEach { review ->
                val login = review.user?.login ?: return@forEach
                val previous = result[login]
                // a plain comment after a verdict does not change the verdict
                if (previous == null || review.state != PullReview.StateEnum.COMMENT ||
                    previous.state == PullReview.StateEnum.COMMENT
                ) {
                    result[login] = review
                }
            }
        return result
    }
}
