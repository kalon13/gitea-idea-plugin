/*
 * Copyright (c) 2023. Leon<leondevlifelog@gmail.com>. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.github.leondevlifelog.gitea.extensions

import com.github.leondevlifelog.gitea.services.GiteaProtectedBranchPatterns
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.config.GitProtectedBranchProvider

/**
 * Exposes to Git the branch protection rules loaded from Gitea by [GiteaProtectedBranchRulesLoader].
 */
class GiteaProtectedBranchProvider : GitProtectedBranchProvider {
    override fun doGetProtectedBranchPatterns(project: Project): List<String> {
        return project.service<GiteaProtectedBranchPatterns>().getPatterns()
    }
}
