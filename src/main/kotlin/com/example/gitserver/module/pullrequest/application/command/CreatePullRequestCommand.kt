package com.example.gitserver.module.pullrequest.application.command

data class CreatePullRequestCommand(
    val repositoryId: Long,
    val authorId: Long,
    val sourceBranch: String,
    val targetBranch: String,
    val title: String,
    val description: String?
)