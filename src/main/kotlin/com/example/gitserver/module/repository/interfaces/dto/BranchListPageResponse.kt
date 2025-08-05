package com.example.gitserver.module.repository.interfaces.dto

data class BranchListPageResponse(
    val content: List<BranchResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)