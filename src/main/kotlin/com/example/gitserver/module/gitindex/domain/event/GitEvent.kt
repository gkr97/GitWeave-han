package com.example.gitserver.module.gitindex.domain.event

data class GitEvent(
    val eventType: String,
    val repositoryId: Long,
    val ownerId: Long,
    val name: String,
    val branch: String? = null,
    val oldrev: String? = null,
    val newrev: String? = null,
)
