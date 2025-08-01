package com.example.gitserver.module.repository.application.command

data class DeleteRepositoryCommand(
    val repositoryId: Long,
    val requesterEmail: String
)