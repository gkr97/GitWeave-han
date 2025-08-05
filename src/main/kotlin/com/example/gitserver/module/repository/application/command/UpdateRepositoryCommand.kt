package com.example.gitserver.module.repository.application.command

data class UpdateRepositoryCommand(
    val repositoryId: Long,
    val requesterId: Long,
    val newName: String,
    val newDescription: String?
)