package com.example.gitserver.common.cache

import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.user.domain.User
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

/** 요청(Request) 범위 캐시 */
@RequestScope
@Component
class RequestCache {

    private val repoById = mutableMapOf<Long, Repository>()
    private val userById = mutableMapOf<Long, User>()

    private data class CollabKey(
        val repoId: Long,
        val userId: Long,
        val requireAccepted: Boolean
    )
    private val collabExists = mutableMapOf<CollabKey, Boolean>()

    private data class BranchKey(
        val repoId: Long,
        val name: String,
        val hint: String?
    )
    private val branchByName = mutableMapOf<BranchKey, Long?>()

    fun getRepo(id: Long) = repoById[id]
    fun putRepo(repo: Repository) { repoById[repo.id!!] = repo }

    fun getUser(id: Long) = userById[id]
    fun putUser(user: User) { userById[user.id] = user }

    fun getCollabExists(repoId: Long, userId: Long, requireAccepted: Boolean): Boolean? =
        collabExists[CollabKey(repoId, userId, requireAccepted)]

    fun putCollabExists(repoId: Long, userId: Long, requireAccepted: Boolean, exists: Boolean) {
        collabExists[CollabKey(repoId, userId, requireAccepted)] = exists
    }

    fun getCollabExists(repoId: Long, userId: Long): Boolean? =
        getCollabExists(repoId, userId, true)

    fun putCollabExists(repoId: Long, userId: Long, exists: Boolean) {
        putCollabExists(repoId, userId, true, exists)
    }

    fun getBranchId(repoId: Long, name: String, hint: String? = null): Long? =
        branchByName[BranchKey(repoId, name, hint)]

    fun putBranchId(repoId: Long, name: String, id: Long?, hint: String? = null) {
        branchByName[BranchKey(repoId, name, hint)] = id
    }
}
