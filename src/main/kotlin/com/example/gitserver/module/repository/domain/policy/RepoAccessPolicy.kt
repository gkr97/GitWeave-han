package com.example.gitserver.module.repository.domain.policy
interface RepoAccessPolicy {
    fun isOwner(repoId: Long, userId: Long): Boolean
    fun canWrite(repoId: Long, userId: Long): Boolean
}