package com.example.gitserver.module.pullrequest.application.query.model

data class PullRequestFileDiffItem(
    val id: Long,
    val filePath: String,
    val oldPath: String?,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val isBinary: Boolean,
    val headBlobHash: String?,
    val baseBlobHash: String?,
    val patch: String?
)
