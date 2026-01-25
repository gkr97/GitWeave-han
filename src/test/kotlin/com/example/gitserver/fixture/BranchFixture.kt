package com.example.gitserver.fixture

import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.user.domain.User
import java.time.LocalDateTime

object BranchFixture {
    fun default(
        id: Long = 1L,
        repository: Repository,
        name: String = "refs/heads/main",
        creator: User? = null,
        headCommitHash: String? = "abc123",
        isDefault: Boolean = true,
        isProtected: Boolean = false,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): Branch {
        return Branch(
            id = id,
            repository = repository,
            name = name,
            creator = creator ?: repository.owner,
            headCommitHash = headCommitHash,
            isDefault = isDefault,
            isProtected = isProtected,
            createdAt = createdAt
        )
    }
}
