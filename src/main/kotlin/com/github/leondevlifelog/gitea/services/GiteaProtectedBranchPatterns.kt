/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */

package com.github.leondevlifelog.gitea.services

import com.intellij.openapi.components.*

/**
 * Protected branch patterns (regex, as expected by Git4Idea) loaded from the Gitea remotes of the project.
 * Persisted in the workspace so they are available before the first fetch.
 */
@Service(Service.Level.PROJECT)
@State(name = "GiteaProtectedBranchPatterns", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GiteaProtectedBranchPatterns : SimplePersistentStateComponent<GiteaProtectedBranchPatterns.PatternsState>(
    PatternsState()
) {
    class PatternsState : BaseState() {
        var patterns by stringSet()
    }

    fun getPatterns(): List<String> = state.patterns.toList()

    fun setPatterns(patterns: Collection<String>) {
        // assigning a new set marks the state as modified
        state.patterns = patterns.toMutableSet()
    }

    companion object {
        /**
         * Converts a Gitea protected branch rule name to a regex.
         * Gitea rule names are either a plain branch name or a glob: `*` does not cross `/`, `**` does.
         */
        fun globToRegex(rule: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < rule.length) {
                val c = rule[i]
                when {
                    c == '*' && i + 1 < rule.length && rule[i + 1] == '*' -> {
                        sb.append(".*")
                        i++
                    }
                    c == '*' -> sb.append("[^/]*")
                    c == '?' -> sb.append("[^/]")
                    "\\.[]{}()+-^$|".indexOf(c) >= 0 -> sb.append('\\').append(c)
                    else -> sb.append(c)
                }
                i++
            }
            return sb.toString()
        }
    }
}
