package com.example.gitserver.module.gitindex.domain.port

import com.example.gitserver.module.repository.interfaces.dto.CommitResponse

interface CommitQueryRepository {
    fun getLatestCommit(repositoryId: Long, branch: String): CommitResponse?
    fun getCommitByHash(repositoryId: Long, commitHash: String): CommitResponse?
    fun existsCommit(repositoryId: Long, commitHash: String): Boolean
    fun getLatestCommitBatch(repositoryId: Long, fullRefs: List<String>): Map<String, CommitResponse?>
}