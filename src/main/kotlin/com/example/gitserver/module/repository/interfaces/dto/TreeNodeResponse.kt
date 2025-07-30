package com.example.gitserver.module.repository.interfaces.dto

data class TreeNodeResponse(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val lastCommitHash: String? = null,
    val lastCommitMessage: String? = null,
    val lastCommittedAt: String? = null,
    val lastCommitter: RepositoryUserResponse? = null,

)