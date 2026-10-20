/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.ui.clone

import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccount
import com.github.leondevlifelog.gitea.authentication.accounts.GiteaAccountManager
import com.github.leondevlifelog.gitea.exception.GiteaMissingTokenException
import com.github.leondevlifelog.gitea.services.GiteaSettings
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.ui.SingleSelectionModel
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.*
import org.gitnex.tea4j.v2.models.Repository
import org.gitnex.tea4j.v2.models.User
import javax.swing.ListSelectionModel

internal class GiteaCloneDialogRepositoryListLoaderImpl : GiteaCloneDialogRepositoryListLoader, Disposable {

    override val loading: Boolean
        get() = jobs.isNotEmpty()
    private val loadingEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

    override val listModel = GiteaCloneDialogRepositoryListModel()
    override val listSelectionModel = SingleSelectionModel()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT + ModalityState.any().asContextElement())
    private val jobs = mutableMapOf<GiteaAccount, Job>()
    private val accountManager = service<GiteaAccountManager>()

    override fun loadRepositories(account: GiteaAccount) {
        if (jobs.containsKey(account)) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val (user, repositories) = withContext(Dispatchers.IO) { fetchRepositories(account) } ?: return@launch
                preservingSelection(listModel, listSelectionModel) {
                    listModel.addRepositories(account, user, repositories)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                preservingSelection(listModel, listSelectionModel) {
                    listModel.setError(account, e)
                }
            } finally {
                jobs.remove(account, coroutineContext.job)
                loadingEventDispatcher.multicaster.eventOccurred()
            }
        }
        jobs[account] = job
        loadingEventDispatcher.multicaster.eventOccurred()
        job.start()
    }

    /**
     * @return the user and its repositories (own repositories first), null when there are none
     */
    private suspend fun fetchRepositories(account: GiteaAccount): Pair<User, List<Repository>>? {
        val token = accountManager.findCredentials(account) ?: throw GiteaMissingTokenException(account)

        val userApi = service<GiteaSettings>().getGiteaApi(account.server.toString(), token).getUserApi()
        val user = userApi.userGetCurrent().execute().body() ?: return null

        val allRepos = mutableListOf<Repository>()
        var page = 1
        val perPage = service<GiteaSettings>().getReposPerPage()
        while (true) {
            currentCoroutineContext().ensureActive()
            val pageResult = userApi.userCurrentListRepos(page, perPage).execute().body() ?: break
            if (pageResult.isEmpty()) break
            allRepos.addAll(pageResult)
            if (pageResult.size < perPage) break
            page += 1
        }

        if (allRepos.isEmpty()) return null
        val userLogin = user.login?.lowercase() ?: ""
        return user to allRepos.sortedWith(compareBy<Repository>({ repo ->
            if ((repo.owner?.login?.lowercase() ?: "") == userLogin) 0 else 1
        }, { repo -> repo.owner?.login?.lowercase() ?: "" }, { repo -> repo.name?.lowercase() ?: "" }))
    }

    override fun clear(account: GiteaAccount) {
        jobs[account]?.cancel()
        listModel.clear(account)
        loadingEventDispatcher.multicaster.eventOccurred()
    }

    override fun addLoadingStateListener(listener: () -> Unit) =
        SimpleEventListener.addListener(loadingEventDispatcher, listener)

    override fun dispose() {
        scope.cancel()
        loadingEventDispatcher.multicaster.eventOccurred()
    }

    companion object {
        private fun preservingSelection(
            listModel: GiteaCloneDialogRepositoryListModel, selectionModel: ListSelectionModel, action: () -> Unit
        ) {
            val selection = if (selectionModel.isSelectionEmpty) {
                null
            } else {
                selectionModel.leadSelectionIndex.let {
                    if (it < 0 || listModel.size == 0) null
                    else listModel.getItemAt(it)
                }
            }
            action()
            if (selection != null) {
                val (account, item) = selection
                val newIdx = listModel.indexOf(account, item)
                if (newIdx >= 0) {
                    selectionModel.setSelectionInterval(newIdx, newIdx)
                }
            }
        }
    }
}
