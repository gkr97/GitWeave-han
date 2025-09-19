package com.example.gitserver.module.pullrequest.application.command

data class ReopenPullRequestCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val requesterId: Long
)