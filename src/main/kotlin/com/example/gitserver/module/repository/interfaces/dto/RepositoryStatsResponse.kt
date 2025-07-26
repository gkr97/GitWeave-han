package com.example.gitserver.module.repository.interfaces.dto

import java.time.LocalDateTime

data class RepositoryStatsResponse(
    val stars: Int,
    val forks: Int,
    val watchers: Int,
    val issues: Int,
    val pullRequests: Int,
    val lastCommitAt: LocalDateTime?
)