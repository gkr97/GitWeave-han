package com.example.gitserver.module.repository.interfaces.dto

import java.time.LocalDateTime

data class CollaboratorResponse(
    val userId: Long,
    val name: String?,
    val email: String,
    val roleCode: String,
    val accepted: Boolean,
    val invitedAt: LocalDateTime
)