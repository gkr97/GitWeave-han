package com.example.gitserver.module.pullrequest.interfaces.dto

data class PullRequestReviewSummary(
    val total: Int,
    val pending: Int,
    val approved: Int,
    val changesRequested: Int,
    val dismissed: Int
)