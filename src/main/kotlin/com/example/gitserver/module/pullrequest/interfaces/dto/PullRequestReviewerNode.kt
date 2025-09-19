package com.example.gitserver.module.pullrequest.interfaces.dto

import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse

data class PullRequestReviewerNode(
    val user: RepositoryUserResponse,
    val status: String,
    val reviewedAt: String?
)