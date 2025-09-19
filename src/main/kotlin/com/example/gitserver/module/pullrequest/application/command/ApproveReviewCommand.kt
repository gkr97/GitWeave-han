package com.example.gitserver.module.pullrequest.application.command

data class ApproveReviewCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val requesterId: Long
)