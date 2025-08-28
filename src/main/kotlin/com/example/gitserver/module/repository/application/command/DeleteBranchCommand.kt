package com.example.gitserver.module.repository.application.command

data class DeleteBranchCommand(
    val repositoryId: Long,
    val branchName: String,
    val requesterId: Long
)