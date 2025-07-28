package com.example.gitserver.module.repository.interfaces.dto

data class RepositoryListPageResponse(
    val content: List<RepositoryListItem>,
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)