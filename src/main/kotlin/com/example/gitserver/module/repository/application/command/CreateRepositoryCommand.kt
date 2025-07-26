package com.example.gitserver.module.repository.application.command

import com.example.gitserver.module.user.domain.User

data class CreateRepositoryCommand(
    val owner: User,
    val name: String,
    val description: String?,
    val visibilityCode: String?,
    val defaultBranch: String = "main",
    val license: String?,
    val language: String?,
    val homepageUrl: String?,
    val topics: String?,

    val initializeReadme: Boolean = false,
    val gitignoreTemplate: String? = null,
    val licenseTemplate: String? = null,
    val invitedUserIds: List<Long>? = null
)