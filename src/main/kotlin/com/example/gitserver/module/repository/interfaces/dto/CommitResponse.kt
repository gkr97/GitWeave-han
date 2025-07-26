package com.example.gitserver.module.repository.interfaces.dto

import java.time.LocalDateTime

data class CommitResponse(
    val hash: String,
    val message: String,
    val committedAt: LocalDateTime,
    val author: RepositoryUserResponse
)