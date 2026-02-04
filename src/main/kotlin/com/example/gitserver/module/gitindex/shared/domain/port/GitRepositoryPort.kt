package com.example.gitserver.module.gitindex.shared.domain.port

import com.example.gitserver.module.gitindex.shared.domain.dto.MergeRequest
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.interfaces.dto.CloneUrlsResponse

interface GitRepositoryPort {
    fun initEmptyGitDirectory(repo: Repository, initializeReadme: Boolean, gitignoreTemplate: String?, licenseTemplate: String?)
    fun deleteGitDirectories(repo: Repository)
    fun getCloneUrls(repo: Repository): CloneUrlsResponse
    fun getHeadCommitHash(repo: Repository, branchName: String): String
    fun renameRepositoryDirectory(ownerId: Long, oldName: String, newName: String)
    fun createBranch(repo: Repository, newBranch: String, sourceBranch: String? = null)
    fun deleteBranch(repo: Repository, branchName: String)
    fun merge(req: MergeRequest): String
}