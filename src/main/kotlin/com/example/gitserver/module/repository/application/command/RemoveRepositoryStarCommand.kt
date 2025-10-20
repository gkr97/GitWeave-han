package com.example.gitserver.module.repository.application.command

data class RemoveRepositoryStarCommand(
    val repositoryId: Long,
    val requesterId: Long
)