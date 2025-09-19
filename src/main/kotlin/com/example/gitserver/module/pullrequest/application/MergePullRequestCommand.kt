package com.example.gitserver.module.pullrequest.application

data class MergePullRequestCommand(
    val repositoryId: Long,
    val pullRequestId: Long,
    val requesterId: Long,
    val mergeType: String = "merge_commit",
    val message: String? = null,
)