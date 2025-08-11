package com.example.gitserver.module.repository.interfaces.dto

data class FileContentResponse(
    val path: String,
    val content: String?,
    val isBinary: Boolean,
    val mimeType: String?,
    val size: Long?,
    val commitHash: String? = null,
    val commitMessage: String? = null,
    val committedAt: String? = null,
    val committer: RepositoryUserResponse? = null,
    val downloadUrl: String? = null,
    val expiresAt: String? = null
)
