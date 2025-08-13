package com.example.gitserver.module.repository.application.query.model

import java.time.LocalDateTime

data class BranchRow(
    val id: Long,
    val repositoryId: Long,
    val name: String,
    val isDefault: Boolean,
    val isProtected: Boolean,
    val createdAt: LocalDateTime,
    val headCommitHash: String?,
    val lastCommitAt: LocalDateTime?,
    val creatorId: Long?,
    val creatorNickname: String?,
    val creatorProfileImageUrl: String?
)