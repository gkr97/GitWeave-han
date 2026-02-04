package com.example.gitserver.module.gitindex.shared.domain.vo


data class ChangedFile(
    val path: String,
    val oldPath: String? = null,
    val status: FileChangeStatus,
    val isBinary: Boolean,
    val additions: Int,
    val deletions: Int,
    val headBlobHash: String?,
    val baseBlobHash: String?
)