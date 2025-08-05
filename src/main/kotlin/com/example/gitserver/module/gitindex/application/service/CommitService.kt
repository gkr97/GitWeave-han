package com.example.gitserver.module.gitindex.application.service

import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse

interface CommitService {
    fun getLatestCommitHash(repositoryId: Long, branch: String): CommitResponse?
    fun getFileTreeAtRoot(repoId: Long, commitHash: String, branch: String?): List<TreeNodeResponse>
    fun getCommitInfo(repositoryId: Long, commitHash: String): CommitResponse?
}