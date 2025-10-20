package com.example.gitserver.module.gitindex.infrastructure.redis

import com.example.gitserver.module.gitindex.domain.port.CommitQueryRepository
import com.example.gitserver.module.gitindex.domain.port.TreeQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@CacheConfig(cacheManager = "cacheManager")
@Component
class GitIndexCache(
    private val commitQueryRepository: CommitQueryRepository,
    private val treeQueryRepository: TreeQueryRepository,
) {

    /**
     * 커밋 메타 — 키: repoId + commitHash
     */
    @Cacheable(
        cacheNames = ["commitByHash"],
        key = "'c:'+#repositoryId+':'+#commitHash",
        unless = "#result == null"
    )
    fun getCommitByHash(
        repositoryId: Long,
        commitHash: String
    ): CommitResponse? =
        commitQueryRepository.getCommitByHash(repositoryId, commitHash)

    /**
     * 루트 트리 — 키: repoId + commitHash + ":root"
     */
    @Cacheable(
        cacheNames = ["treeAtRoot"],
        key = "'t:'+#repositoryId+':'+#commitHash+':root'"
    )
    fun getFileTreeAtRoot(
        repositoryId: Long,
        commitHash: String,
        fullRefOrNull: String?
    ): List<TreeNodeResponse> =
        treeQueryRepository.getFileTreeAtRoot(repositoryId, commitHash, fullRefOrNull)
}