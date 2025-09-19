package com.example.gitserver.module.pullrequest.application.query.model

import java.time.LocalDateTime

data class PullRequestListItem(
    val id: Long,
    val repositoryId: Long,
    val title: String,
    val status: String,
    val sourceBranch: String,
    val targetBranch: String,
    val author: RepositoryUser,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
)