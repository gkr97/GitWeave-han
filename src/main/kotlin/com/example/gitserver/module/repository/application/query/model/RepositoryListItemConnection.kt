package com.example.gitserver.module.repository.application.query.model

import com.example.gitserver.common.pagination.PageInfoDTO

data class RepositoryListItemConnection(
    val edges: List<RepositoryListItemEdge>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null
)