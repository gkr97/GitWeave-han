package com.example.gitserver.module.repository.application.command

data class CreateBranchCommand(
    val repositoryId: Long,
    val branchName: String,
    val sourceBranch: String?,
    val requesterId: Long
)