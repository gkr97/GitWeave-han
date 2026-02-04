package com.example.gitserver.module.gitindex.indexer.application.query

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.shared.domain.port.CommitQueryRepository
import com.example.gitserver.module.gitindex.indexer.infrastructure.redis.GitIndexCache
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Objects

@Service
class CommitQueryService(
    private val commitQueryRepository: CommitQueryRepository,
    private val cache: GitIndexCache,
) {

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = ["fileTree"],
        key = "T(java.util.Objects).hash(#repoId, #commitHash, #branch)"
    )
    fun getFileTreeAtRoot(
        repoId: Long,
        commitHash: String,
        branch: String?
    ): List<TreeNodeResponse> {
        return cache.getFileTreeAtRoot(repoId, commitHash, GitRefUtils.toFullRefOrNull(branch))
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = ["latestCommit"],
        key = "T(java.util.Objects).hash(#repositoryId, #branch)"
    )
    fun getLatestCommitHash(repositoryId: Long, branch: String): CommitResponse? {
        return commitQueryRepository.getLatestCommit(repositoryId, GitRefUtils.toFullRef(branch))
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = ["commitInfo"],
        key = "T(java.util.Objects).hash(#repositoryId, #commitHash)"
    )
    fun getCommitInfo(repositoryId: Long, commitHash: String): CommitResponse? {
        return cache.getCommitByHash(repositoryId, commitHash)
    }

    @Transactional(readOnly = true)
    fun getCommitInfoBatch(
        repositoryId: Long,
        commitHashes: List<String>
    ): Map<String, CommitResponse?> {
        if (commitHashes.isEmpty()) return emptyMap()
        val distinct = commitHashes.distinct()
        val byHash = commitQueryRepository.getCommitByHashBatch(repositoryId, distinct)

        val out = LinkedHashMap<String, CommitResponse?>(commitHashes.size)
        for (h in commitHashes) {
            out[h] = byHash[h]
        }
        return out
    }

    @Transactional(readOnly = true)
    fun getLatestCommitHashBatch(
        repositoryId: Long,
        refs: List<String>
    ): Map<String, CommitResponse?> {
        if (refs.isEmpty()) return emptyMap()

        val inToFull: Map<String, String> =
            refs.distinct().associateWith { GitRefUtils.toFullRef(it) }

        val fullToCommit: Map<String, CommitResponse?> =
            commitQueryRepository.getLatestCommitBatch(repositoryId, inToFull.values.toList())

        val out = LinkedHashMap<String, CommitResponse?>(refs.size)
        for (r in refs) {
            out[r] = fullToCommit[inToFull[r]]
        }
        return out
    }
}
