package com.example.gitserver.module.repository.domain.event

import java.time.LocalDateTime

data class SyncBranchEvent(
    val repositoryId: Long,
    val branchName: String,
    val newHeadCommit: String?,
    val lastCommitAtUtc: LocalDateTime?
)