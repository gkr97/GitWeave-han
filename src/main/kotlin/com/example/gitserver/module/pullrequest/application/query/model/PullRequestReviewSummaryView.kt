package com.example.gitserver.module.pullrequest.application.query.model

data class PullRequestReviewSummaryView(
    val total: Int, val pending: Int, val approved: Int, val changesRequested: Int, val dismissed: Int
)
