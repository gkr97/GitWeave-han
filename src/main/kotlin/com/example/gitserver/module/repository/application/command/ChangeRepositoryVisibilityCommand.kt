package com.example.gitserver.module.repository.application.command

data class ChangeRepositoryVisibilityCommand(
    val repositoryId: Long,
    val requesterId: Long,
    val newVisibility: String,
)