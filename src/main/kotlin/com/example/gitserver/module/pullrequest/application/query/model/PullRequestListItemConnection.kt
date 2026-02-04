package com.example.gitserver.module.pullrequest.application.query.model

import com.example.gitserver.common.pagination.PageInfoDTO

data class PullRequestListItemConnection(
    val edges: List<PullRequestListItemEdge>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null
)

data class PullRequestListItemEdge(
    val cursor: String,
    val node: PullRequestListItem
)
