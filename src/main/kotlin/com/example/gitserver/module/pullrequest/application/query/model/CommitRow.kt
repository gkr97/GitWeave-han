package com.example.gitserver.module.pullrequest.application.query.model

import java.time.LocalDateTime

data class CommitRow(
    val commitHash: String,
    val seq: Int
)
