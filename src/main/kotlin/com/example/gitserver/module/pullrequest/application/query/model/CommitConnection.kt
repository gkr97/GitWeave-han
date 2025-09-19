package com.example.gitserver.module.pullrequest.application.query.model

import com.example.gitserver.common.pagination.PageInfoDTO

data class CommitConnection(
    val edges: List<CommitEdge>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null
)