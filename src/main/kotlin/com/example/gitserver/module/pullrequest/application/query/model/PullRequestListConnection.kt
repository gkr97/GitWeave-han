package com.example.gitserver.module.pullrequest.application.query.model

data class PullRequestListConnection(
    val content: List<PullRequestListItem>,
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    val hasNext: Boolean
)