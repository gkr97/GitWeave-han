package com.example.gitserver.module.gitindex.domain

import com.example.gitserver.module.gitindex.domain.vo.CommitHash
import com.example.gitserver.module.gitindex.domain.vo.FilePath
import java.time.Instant

/**
 * Tree(BlobTree) 객체
 */
data class BlobTree(
    val repositoryId: Long,
    val commitHash: CommitHash,
    val path: FilePath,
    val name: String,
    val isDirectory: Boolean,
    val fileHash: String?,
    val size: Long?,
    val depth: Int,
    val fileTypeCodeId: Long?,
    val lastModifiedAt: Instant?
)