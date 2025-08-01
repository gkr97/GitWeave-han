package com.example.gitserver.module.repository.interfaces.dto

data class UserRepositoryListPageResponse(
    val content: List<RepositoryListItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)