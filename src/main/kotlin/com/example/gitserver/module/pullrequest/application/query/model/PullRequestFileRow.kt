package com.example.gitserver.module.pullrequest.application.query.model

data class PullRequestFileRow(
    val path: String,
    val oldPath: String?,
    val statusCodeId: Long,
    val isBinary: Boolean,
    val additions: Int,
    val deletions: Int,
    val headBlobHash: String?,
    val baseBlobHash: String?
)