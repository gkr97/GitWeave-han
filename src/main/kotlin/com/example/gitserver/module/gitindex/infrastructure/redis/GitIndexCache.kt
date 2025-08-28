package com.example.gitserver.module.gitindex.infrastructure.redis

import com.example.gitserver.module.gitindex.domain.port.BlobQueryRepository
import com.example.gitserver.module.gitindex.domain.port.CommitQueryRepository
import com.example.gitserver.module.gitindex.domain.port.TreeQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component


@CacheConfig(cacheManager = "cacheManager")
@Component
class GitIndexCache(
    private val commitQueryRepository : CommitQueryRepository,
    private val treeQueryRepository : TreeQueryRepository,
    private val blobQueryRepository : BlobQueryRepository,
) {

    /**
     * 커밋 메타 — 키: repoId + commitHash
     */
    @Cacheable(cacheNames = ["commitByHash"], key = "'c:'+#repositoryId+':'+#commitHash", unless = "#result == null")
    fun getCommitByHash(repositoryId: Long, commitHash: String): CommitResponse? =
        commitQueryRepository.getCommitByHash(repositoryId, commitHash)

    /**
     * 루트 트리 — 키: repoId + commitHash + ":root"
     */
    @Cacheable(cacheNames = ["treeAtRoot"], key = "'t:'+#repositoryId+':'+#commitHash+':root'")
    fun getFileTreeAtRoot(repositoryId: Long, commitHash: String, fullRefOrNull: String?) =
        treeQueryRepository.getFileTreeAtRoot(repositoryId, commitHash, fullRefOrNull)

    /**
     * 특정 경로 트리 아이템 — 키: repoId + commitHash + path
     */
    @Cacheable(cacheNames = ["treeByPath"], key = "'tp:'+#repositoryId+':'+#commitHash+':'+#path")
    fun getTreeItem(repositoryId: Long, commitHash: String, path: String) =
        treeQueryRepository.getTreeItem(repositoryId, commitHash, path)

    /**
     * 블롭 메타 — 키: repoId + fileHash
     */
    @Cacheable(cacheNames = ["blobMeta"], key = "'b:'+#repositoryId+':'+#fileHash")
    fun getBlobMeta(repositoryId: Long, fileHash: String) =
        blobQueryRepository.getBlobMeta(repositoryId, fileHash)
}
