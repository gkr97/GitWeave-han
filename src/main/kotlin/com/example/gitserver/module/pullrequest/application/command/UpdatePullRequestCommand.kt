package com.example.gitserver.module.pullrequest.application.command

data class UpdatePullRequestCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val requesterId: Long,
    val title: String?,
    val description: String?
)