package com.example.gitserver.module.repository.domain.event

data class BranchCreated(
    val repositoryId: Long,
    val fullRef: String,
    val headCommit: String?
)

data class BranchDeleted(
    val repositoryId: Long,
    val fullRef: String
)

data class RepositoryVisibilityChanged(
    val repositoryId: Long,
    val visibilityCode: String
)

data class CollaboratorInvited(val repositoryId: Long, val userId: Long)
data class CollaboratorAccepted(val repositoryId: Long, val userId: Long)
data class CollaboratorRejected(val repositoryId: Long, val userId: Long)
data class CollaboratorRemoved(val repositoryId: Long, val userId: Long)

data class RepositoryCreated(val repositoryId: Long, val ownerId: Long, val name: String)
data class RepositoryRenamed(val repositoryId: Long, val oldName: String, val newName: String)

data class RepositoryDeleted(val repositoryId: Long)
