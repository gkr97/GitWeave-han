package com.example.gitserver.module.pullrequest.application.command

data class AssignReviewerCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val requesterId: Long,
    val reviewerId: Long
)