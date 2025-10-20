package com.example.gitserver.module.repository.application.command

data class AddRepositoryStarCommand(
    val repositoryId: Long,
    val requesterId: Long
)
