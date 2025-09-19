package com.example.gitserver.module.pullrequest.application.command

data class CreatePullRequestCommentCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val authorId: Long,
    val content: String,
    val commentType: String,
    val filePath: String?,
    val lineNumber: Int?
)