/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.api

import com.github.leondevlifelog.gitea.services.GiteaSettings
import org.gitnex.tea4j.v2.models.CreateFileOptions
import org.gitnex.tea4j.v2.models.CreatePullRequestOption
import org.gitnex.tea4j.v2.models.PullRequest
import retrofit2.Call
import java.util.*

/**
 * Real Gitea instance for the integration tests, configured by environment variables:
 * - GITEA_TEST_URL, e.g. http://127.0.0.1:3000
 * - GITEA_TEST_TOKEN, token of a user with write access to the repository
 * - GITEA_TEST_REPO, owner/repo of a disposable repository: tests create issues, branches and pull requests
 * - GITEA_TEST_TOKEN_OTHER (optional), token of a second user with write access, for reviews
 *
 * Needs the platform (GiteaSettings): use it from a BasePlatformTestCase.
 */
object GiteaTestServer {
    val url: String? = System.getenv("GITEA_TEST_URL")
    private val token: String? = System.getenv("GITEA_TEST_TOKEN")
    val otherToken: String? = System.getenv("GITEA_TEST_TOKEN_OTHER")?.takeIf { it.isNotBlank() }
    private val repo: String? = System.getenv("GITEA_TEST_REPO")

    val isConfigured: Boolean get() = !url.isNullOrBlank() && !token.isNullOrBlank() && !repo.isNullOrBlank()

    val path: GiteaRepositoryPath get() = GiteaRepositoryPath(repo!!.substringBefore('/'), repo.substringAfter('/'))

    fun api(token: String = this.token!!): GiteaApi = GiteaSettings.getInstance().getGiteaApi(url!!, token)

    fun client(token: String = this.token!!): GiteaRepositoryClient = GiteaRepositoryClient(api(token), path)

    val defaultBranch: String get() = execute(api().getRepoApi().repoGet(path.owner, path.repository)).defaultBranch

    /**
     * Creates a branch adding one file to the default branch, and a pull request from it.
     */
    fun createPullRequest(title: String): PullRequest {
        val branch = "test-${System.nanoTime()}"
        val base = defaultBranch
        val file = CreateFileOptions().apply {
            this.branch = base
            newBranch = branch
            message = "Add $branch"
            content = Base64.getEncoder().encodeToString("line 1\nline 2\n".toByteArray())
        }
        execute(api().getRepoApi().repoCreateFile(file, path.owner, path.repository, "tests/$branch.txt"))
        val option = CreatePullRequestOption().apply {
            head = branch
            this.base = base
            this.title = title
            body = "Pull request **body**"
        }
        return execute(api().getRepoApi().repoCreatePullRequest(path.owner, path.repository, option))
    }

    fun <T> execute(call: Call<T>): T {
        val response = call.execute()
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()}" }
        return response.body()!!
    }
}
