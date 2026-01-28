package com.example.gitserver.module.search.interfaces.dto

import com.example.gitserver.module.search.domain.RepositoryDoc

data class UnifiedSearchResponse(
    val repositories: RepositorySearchResult,
    val users: UserSearchResult
)

data class RepositorySearchResult(
    val items: List<RepositoryDoc>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val hasNext: Boolean
)

data class UserSearchResult(
    val items: List<UserSearchDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val hasNext: Boolean
)