package com.example.gitserver.module.pullrequest.application

data class RequestChangesCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val requesterId: Long,
    val reason: String? = null
)