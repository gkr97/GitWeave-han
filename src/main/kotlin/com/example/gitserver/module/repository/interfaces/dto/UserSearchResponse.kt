package com.example.gitserver.module.repository.interfaces.dto

data class UserSearchResponse(
    val id: Long,
    val name: String,
    val email: String,
    val profileImageUrl: String?
)