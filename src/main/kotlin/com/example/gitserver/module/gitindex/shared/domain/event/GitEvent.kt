package com.example.gitserver.module.gitindex.shared.domain.event

data class GitEvent(
    val eventType: String = "UNKNOWN",
    val repositoryId: Long,
    val ownerId: Long,
    val name: String,
    val branch: String? = null,
    val oldrev: String? = null,
    val newrev: String? = null,
    val actorId: Long? = null,
    val collaboratorUserId: Long? = null,
    val oldName: String? = null,
    val newName: String? = null,
    val visibilityCode: String? = null
)
