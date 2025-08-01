package com.example.gitserver.module.repository.interfaces.dto

data class UserRepositoryListRequest(
    val page: Int = 1,
    val size: Int = 10,
    val sortBy: String = "lastUpdatedAt",
    val sortDirection: String = "DESC",
    val keyword: String? = null
)
