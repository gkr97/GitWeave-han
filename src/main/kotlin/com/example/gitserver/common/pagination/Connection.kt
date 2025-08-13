package com.example.gitserver.common.pagination

data class Connection<T>(
    val edges: List<Edge<T>>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null
)
