package com.example.gitserver.module.gitindex.domain

import com.example.gitserver.module.gitindex.domain.vo.CommitHash
import com.example.gitserver.module.gitindex.domain.vo.TreeHash
import java.time.LocalDateTime

/**
 * Commit 객체
 */
data class Commit(
    val repositoryId: Long,
    val hash: CommitHash,
    val message: String,
    val authorId: Long?,
    val authorName: String,
    val authorEmail: String,
    val committedAt: java.time.Instant,
    val committerName: String,
    val committerEmail: String,
    val treeHash: TreeHash,
    val parentHashes: List<String> = emptyList(),
    val isMerge: Boolean = false,
    val createdAt: java.time.Instant,
    val branch: String,
)