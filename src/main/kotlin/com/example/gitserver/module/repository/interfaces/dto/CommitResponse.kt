package com.example.gitserver.module.repository.interfaces.dto


import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class CommitResponse(
    val hash: String,
    val message: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    val committedAt: LocalDateTime,
    val author: RepositoryUserResponse
)