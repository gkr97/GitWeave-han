package com.example.gitserver.module.repository.application.query.model

import java.time.LocalDateTime

data class RepoRow(
    val id: Long,
    val name: String,
    val description: String?,
    val updatedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val visibilityCodeId: Long?,
    val language: String?,
    val ownerId: Long,
    val ownerName: String?,
    val ownerProfileImageUrl: String?
)