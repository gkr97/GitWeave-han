package com.example.gitserver.module.repository.interfaces.dto

data class RepositoryUserResponse(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?
)