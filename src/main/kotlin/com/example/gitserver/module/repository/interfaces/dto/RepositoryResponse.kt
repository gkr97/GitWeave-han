package com.example.gitserver.module.repository.interfaces.dto

import com.example.gitserver.module.repository.domain.Repository
import java.time.LocalDateTime

data class RepositoryResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val defaultBranch: String,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(entity: Repository): RepositoryResponse {
            return RepositoryResponse(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                defaultBranch = entity.defaultBranch,
                createdAt = entity.createdAt
            )
        }
    }
}