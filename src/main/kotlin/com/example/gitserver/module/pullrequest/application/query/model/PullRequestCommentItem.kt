package com.example.gitserver.module.pullrequest.application.query.model

data class PullRequestCommentItem(
    val id: Long,
    val authorId: Long,
    val content: String,
    val commentType: String,
    val filePath: String?,
    val lineNumber: Int?,
    val createdAt: String
)