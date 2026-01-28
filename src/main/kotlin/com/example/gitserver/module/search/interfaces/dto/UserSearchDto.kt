package com.example.gitserver.module.search.interfaces.dto

data class UserSearchDto(
    val id: Long,
    val username: String,
    val avatarUrl: String?,
    val repoCount: Long?
)