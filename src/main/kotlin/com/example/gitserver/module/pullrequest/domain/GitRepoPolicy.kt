package com.example.gitserver.module.pullrequest.domain

interface GitRepoPolicy {
    fun isCollaboratorOrOwner(repositoryId: Long, userId: Long): Boolean
    fun branchExists(repositoryId: Long, fullRef: String): Boolean
    fun getHeadCommitHash(repositoryId: Long, fullRef: String): String
}