package com.example.gitserver.module.repository.domain.event

data class RepositoryCreatedEvent(
    val repositoryId: Long,
    val ownerId: Long,
    val name: String
)