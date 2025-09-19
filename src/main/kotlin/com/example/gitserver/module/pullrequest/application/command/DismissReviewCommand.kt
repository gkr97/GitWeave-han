package com.example.gitserver.module.pullrequest.application.command

data class DismissReviewCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val requesterId: Long,
    val targetReviewerId: Long? = null
)