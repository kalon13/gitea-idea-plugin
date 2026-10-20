/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.services

import com.github.leondevlifelog.gitea.api.GiteaRepositoryPath
import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccount
import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccountManager
import com.github.leondevlifelog.gitea.authentication.accounts.GiteaProjectDefaultAccountHolder
import com.github.leondevlifelog.gitea.util.GiteaUrlUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.GitHostingUrlUtil.match
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A Gitea repository reachable from a git remote of the project, with the account used to access it.
 */
data class GiteaRepositoryMapping(
    val account: GiteaAccount,
    val path: GiteaRepositoryPath,
    val gitRepository: GitRepository,
    val remote: GitRemote,
) {
    /** Identity of the mapping, independent of the git objects. */
    val key: String get() = "${account.id}|${account.server}/$path"

    val webUrl: String get() = "${account.server}/$path"

    override fun toString(): String = path.toString()
}

@Service(Service.Level.PROJECT)
class GiteaProjectRepositories(private val project: Project, private val scope: CoroutineScope) {

    /** Scope for UI of the project, cancelled when the project closes. */
    fun childScope(context: CoroutineContext = EmptyCoroutineContext): CoroutineScope =
        CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job) + context)

    /**
     * Gitea repositories of the project. The `origin` remote comes first; for a server with several accounts
     * the default account of the project wins.
     */
    fun findMappings(): List<GiteaRepositoryMapping> {
        val accounts = service<GiteaAccountManager>().accountsState.value
        if (accounts.isEmpty()) return emptyList()
        val defaultAccount = project.service<GiteaProjectDefaultAccountHolder>().account

        val result = LinkedHashMap<String, GiteaRepositoryMapping>()
        for (repository in GitRepositoryManager.getInstance(project).repositories) {
            val remotes = repository.remotes.sortedBy { if (it.name == GitRemote.ORIGIN) 0 else 1 }
            for (remote in remotes) {
                for (url in remote.urls) {
                    val path = GiteaUrlUtil.getUserAndRepositoryFromRemoteUrl(url) ?: continue
                    val candidates = accounts.filter { match(it.server.toURI(), url) }
                    val account = candidates.firstOrNull { it == defaultAccount } ?: candidates.firstOrNull() ?: continue
                    val mapping = GiteaRepositoryMapping(account, path, repository, remote)
                    result.putIfAbsent(mapping.key, mapping)
                }
            }
        }
        return result.values.toList()
    }

    companion object {
        fun getInstance(project: Project): GiteaProjectRepositories = project.service()
    }
}
