/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Markdown rendering endpoint.
 *
 * Not taken from tea4j: Gitea's MarkdownOption has no json tags, and builds using Go's json v2 (e.g. 1.27)
 * match field names case-sensitively, so only "Text"/"Mode"/"Context" are understood while older builds
 * accept any case. See [GiteaMarkdownApi.options].
 */
interface GiteaMarkdownApi {
    @POST("markdown")
    fun render(@Body options: Map<String, @JvmSuppressWildcards Any>): Call<ResponseBody>

    companion object {
        /**
         * Options understood by every Gitea version: each key is sent with both casings, json v1 takes the last
         * match (same value), json v2 only matches the capitalized one.
         */
        fun options(text: String, mode: String, context: String): Map<String, Any> = linkedMapOf(
            "text" to text, "Text" to text,
            "mode" to mode, "Mode" to mode,
            "context" to context, "Context" to context
        )
    }
}
