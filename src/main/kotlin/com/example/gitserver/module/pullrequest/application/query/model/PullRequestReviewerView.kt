package com.example.gitserver.module.pullrequest.application.query.model

data class PullRequestReviewerView(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val status: String,
    val reviewedAt: String?
)