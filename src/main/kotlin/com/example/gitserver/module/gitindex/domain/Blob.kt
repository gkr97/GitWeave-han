package com.example.gitserver.module.gitindex.domain

import com.example.gitserver.module.gitindex.domain.vo.BlobHash
import com.example.gitserver.module.gitindex.domain.vo.FilePath

/**
 * Blob 객체
 */
data class Blob(
    val repositoryId: Long,
    val hash: BlobHash,
    val path: FilePath?,
    val extension: String?,
    val mimeType: String?,
    val isBinary: Boolean,
    val fileSize: Long,
    val lineCount: Int?,
    val externalStorageKey: String,
    val createdAt: java.time.Instant
)