package com.example.gitserver.module.pullrequest.application.command

data class DeletePullRequestCommentCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val commentId: Long,
    val requesterId: Long
)