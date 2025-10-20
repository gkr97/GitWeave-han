package com.example.gitserver.module.repository.interfaces.dto

import java.time.LocalDateTime

data class BranchResponse(
    val name: String,
    val qualifiedName: String,
    val isDefault: Boolean,
    val isProtected: Boolean,
    val createdAt: LocalDateTime,
    val headCommit: CommitResponse,
    val creator: RepositoryUserResponse?,
    val _repositoryId: Long
)
