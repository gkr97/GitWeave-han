package com.example.gitserver.module.pullrequest.application.query.model

data class RepositoryUser(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?
)