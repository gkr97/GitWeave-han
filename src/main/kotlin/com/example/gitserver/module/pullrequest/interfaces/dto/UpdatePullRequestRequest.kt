package com.example.gitserver.module.pullrequest.interfaces.dto

data class UpdatePullRequestRequest(
    val title: String? = null,
    val description: String? = null
)