/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import com.github.leondevlifelog.gitea.services.GiteaSettings
import com.intellij.util.net.ssl.CertificateManager
import okhttp3.OkHttpClient
import org.gitnex.tea4j.v2.apis.IssueApi
import org.gitnex.tea4j.v2.apis.RepositoryApi
import org.gitnex.tea4j.v2.apis.UserApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Gitea Api
 * @param baseUrl Base Url
 * @param token Access Token
 * @see AccessTokenAuth
 * @author Leon
 */
class GiteaApi(baseUrl: String, token: String) {

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("${baseUrl.trimEnd('/')}/api/v1/")
        .client(baseClient.newBuilder().addInterceptor(AccessTokenAuth(token)).applyTimeout().build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun getIssueApi(): IssueApi {
        return retrofit.create(IssueApi::class.java)
    }

    fun getUserApi(): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    fun getRepoApi(): RepositoryApi {
        return retrofit.create(RepositoryApi::class.java)
    }

    companion object {
        /**
         * Shared by all instances, so they reuse the same connection pool and dispatcher.
         * Certificates go through the IDE CertificateManager: self-signed certs accepted by the user work.
         */
        private val baseClient: OkHttpClient by lazy {
            OkHttpClient.Builder().sslSocketFactory(
                CertificateManager.getInstance().sslContext.socketFactory, CertificateManager.getInstance().trustManager
            ).build()
        }

        /** Some requests (e.g. merging a large pull request) take long on a healthy server. */
        private const val MIN_READ_TIMEOUT_MS = 60_000L

        /**
         * The setting is the connection timeout; reads wait at least [MIN_READ_TIMEOUT_MS].
         * 0 means no timeout, both in the settings UI and for OkHttp.
         */
        private fun OkHttpClient.Builder.applyTimeout(): OkHttpClient.Builder {
            val timeout = GiteaSettings.getInstance().getConnectionTimeout().toLong()
            val readTimeout = if (timeout == 0L) 0L else maxOf(timeout, MIN_READ_TIMEOUT_MS)
            return connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(readTimeout, TimeUnit.MILLISECONDS)
        }
    }
}
