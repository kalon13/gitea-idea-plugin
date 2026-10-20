/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.issues

import com.github.leondevlifelog.gitea.api.GiteaRepositoryClient
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.swing.JEditorPane

/**
 * Comments of an issue or pull request with their markdown rendered by the server, in parallel.
 */
suspend fun loadRenderedComments(client: GiteaRepositoryClient, number: Long): List<GiteaIssueHtml.RenderedComment> =
    coroutineScope {
        client.getComments(number).map { comment ->
            async {
                GiteaIssueHtml.RenderedComment(
                    comment.user?.login.orEmpty(), comment.createdAt, client.renderMarkdown(comment.body)
                )
            }
        }.awaitAll()
    }

/**
 * Read-only pane for the pages built by [GiteaIssueHtml]; links open in the browser.
 */
fun createGiteaHtmlPane(): JEditorPane = JEditorPane().apply {
    editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build().also {
        GiteaIssueHtml.addMarkdownRules(it.styleSheet)
    }
    isEditable = false
    border = JBUI.Borders.empty(8)
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
}
