/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.services

import com.github.leondevlifelog.gitea.api.GiteaApi
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "GiteaSettings", storages = [Storage(value = "gitea.xml")], category = SettingsCategory.TOOLS
)
class GiteaSettings : PersistentStateComponent<GiteaSettings.State> {
    private var myState = State()
    fun getGiteaApi(baseUrl: String, token: String): GiteaApi {
        return GiteaApi(baseUrl, token)
    }

    fun getReposPerPage(): Int = myState.REPOS_PER_PAGE
    fun setReposPerPage(value: Int) { myState.REPOS_PER_PAGE = value.coerceAtMost(50).coerceAtLeast(1) }
    class State {
        var REPOS_PER_PAGE = 50
        var CONNECTION_TIMEOUT = 5000
        var CLONE_GIT_USING_SSH = false
        var SSH_PORT = 22
        var SSH_USER = "git"
    }

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        @JvmStatic
        fun getInstance(): GiteaSettings {
            return ApplicationManager.getApplication().getService(GiteaSettings::class.java)
        }
    }

    fun getSshPort(): Int {
        return myState.SSH_PORT
    }

    fun getConnectionTimeout(): Int {
        return myState.CONNECTION_TIMEOUT
    }

    fun setConnectionTimeout(timeout: Int) {
        myState.CONNECTION_TIMEOUT = timeout
    }

    fun isCloneGitUsingSsh(): Boolean {
        return myState.CLONE_GIT_USING_SSH
    }

    fun setCloneGitUsingSsh(value: Boolean) {
        myState.CLONE_GIT_USING_SSH = value
    }

    fun setSshPort(port: Int) {
        myState.SSH_PORT = port
    }

    fun getSshUser(): String {
        return myState.SSH_USER
    }

    fun setSshUser(user: String) {
        myState.SSH_USER = user.trim().ifEmpty { "git" }
    }

}
