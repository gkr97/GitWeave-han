package com.example.gitserver.module.gitindex.application.service

import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.interfaces.dto.CloneUrlsResponse

interface GitService {
    fun initEmptyGitDirectory(
        repository: Repository,
        initializeReadme: Boolean,
        gitignoreTemplate: String?,
        licenseTemplate: String?
    )
    fun deleteGitDirectories(repository: Repository)

    fun getCloneUrls(repository: Repository): CloneUrlsResponse

    fun getHeadCommitHash(repository: Repository, branchName: String): String?

    fun renameRepositoryDirectory(ownerId: Long, oldName: String, newName: String)

    fun createBranch(repository: Repository, newBranch: String, sourceBranch: String? = null)

    fun deleteBranch(repository: Repository, branchName: String)
}