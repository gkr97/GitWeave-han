package com.example.gitserver.module.repository.application.service

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
    fun getCloneUrls(repoId: Long): CloneUrlsResponse
    fun getHeadCommitHash(repository: Repository, branchName: String): String?
}