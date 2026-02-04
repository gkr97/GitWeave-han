package com.example.gitserver.module.pullrequest.domain.policy

interface PullRequestAccessPolicy {
    fun canAct(repoId: Long, userId: Long, authorId: Long? = null): Boolean
    fun canMaintain(repoId: Long, userId: Long): Boolean
}
