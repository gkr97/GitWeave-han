package com.example.gitserver.module.pullrequest.interfaces.dto

data class PullRequestCommentResponse(
    val id: Long,
    val authorId: Long,
    val content: String,
    val commentType: String,
    val filePath: String?,
    val lineNumber: Int?,
    val createdAt: String
)