package com.example.gitserver.module.gitindex.shared.domain

import com.example.gitserver.module.gitindex.shared.domain.vo.CommitHash
import com.example.gitserver.module.gitindex.shared.domain.vo.TreeHash
import java.time.Instant
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
    val committedAt: Instant,
    val committerName: String,
    val committerEmail: String,
    val treeHash: TreeHash,
    val parentHashes: List<String> = emptyList(),
    val isMerge: Boolean = false,
    val createdAt: Instant,
    val branch: String,
)