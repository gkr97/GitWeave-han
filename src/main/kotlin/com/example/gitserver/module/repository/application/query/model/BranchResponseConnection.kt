package com.example.gitserver.module.repository.application.query.model


import com.example.gitserver.common.pagination.PageInfoDTO


data class BranchResponseConnection(
    val edges: List<BranchEdge>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null
)