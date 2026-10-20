/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.util

import com.github.leondevlifelog.gitea.authentication.accounts.GiteaServerPath
import com.github.leondevlifelog.gitea.services.GiteaSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.gitnex.tea4j.v2.models.Repository

/**
 * Utilities for Gitea-Git interactions
 */
@Service
class GiteaGitHelper {

    fun getRemoteUrl(server: GiteaServerPath, user: String, repo: String): String {
        val settings = GiteaSettings.getInstance()
        return if (settings.isCloneGitUsingSsh()) {
            server.toSshCloneUrl(user, repo, settings.getSshPort(), settings.getSshUser())
        } else {
            server.toHttpCloneUrl(user, repo)
        }
    }

    /**
     * Prefer the clone urls returned by the server: they already reflect SSH_DOMAIN, SSH_PORT, the ssh user
     * and ROOT_URL of the instance, which can differ from the API host (e.g. behind a reverse proxy).
     */
    fun getRemoteUrl(server: GiteaServerPath, repository: Repository): String {
        val serverUrl = if (GiteaSettings.getInstance().isCloneGitUsingSsh()) repository.sshUrl else repository.cloneUrl
        return serverUrl?.takeIf { it.isNotBlank() } ?: getRemoteUrl(server, repository.owner.login, repository.name)
    }

    companion object {

        @JvmStatic
        fun getInstance(): GiteaGitHelper {
            return service()
        }
    }
}
