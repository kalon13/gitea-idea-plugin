/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.github.leondevlifelog.gitea.extensions

import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccountManager
import com.github.leondevlifelog.gitea.services.GiteaProtectedBranchPatterns
import com.github.leondevlifelog.gitea.services.GiteaSettings
import com.github.leondevlifelog.gitea.util.GiteaUrlUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import git4idea.fetch.GitFetchHandler
import git4idea.remote.hosting.GitHostingUrlUtil.match
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

/**
 * After a fetch, loads the branch protection rules of the fetched Gitea remotes.
 * Remotes without a matching Gitea account are skipped silently.
 */
class GiteaProtectedBranchRulesLoader : GitFetchHandler {
    override fun doAfterSuccessfulFetch(
        project: Project, fetches: Map<GitRepository, List<GitRemote>>, indicator: ProgressIndicator
    ) {
        val accountManager = service<GiteaAccountManager>()
        val accounts = accountManager.accountsState.value
        if (accounts.isEmpty()) return

        val patterns = mutableSetOf<String>()
        var loaded = false
        for (url in fetches.values.flatten().flatMap { it.urls }.distinct()) {
            indicator.checkCanceled()
            val account = accounts.firstOrNull { match(it.server.toURI(), url) } ?: continue
            val path = GiteaUrlUtil.getUserAndRepositoryFromRemoteUrl(url) ?: continue
            val token = runBlockingMaybeCancellable { accountManager.findCredentials(account) } ?: continue
            try {
                val response = service<GiteaSettings>().getGiteaApi(account.server.toString(), token).getRepoApi()
                    .repoListBranchProtection(path.owner, path.repository).execute()
                // 403/404: the user cannot read the settings of the repository, nothing to load
                val rules = response.body() ?: continue
                rules.mapNotNullTo(patterns) { rule ->
                    (rule.ruleName ?: rule.branchName)?.takeIf { it.isNotBlank() }
                        ?.let(GiteaProtectedBranchPatterns::globToRegex)
                }
                loaded = true
            } catch (e: Exception) {
                LOG.info("Cannot load protected branches of $url", e)
            }
        }
        if (loaded) {
            project.service<GiteaProtectedBranchPatterns>().setPatterns(patterns)
        }
    }

    companion object {
        private val LOG = logger<GiteaProtectedBranchRulesLoader>()
    }
}
