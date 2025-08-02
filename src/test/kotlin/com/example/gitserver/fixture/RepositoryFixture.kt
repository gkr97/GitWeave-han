package com.example.gitserver.fixture

import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.user.domain.User
import java.time.LocalDateTime

object RepositoryFixture {
    fun default(
        id: Long = 1L,
        owner: User,
        name: String = "테스트저장소",
        description: String? = "기본 설명",
        visibilityCodeId: Long? = 1L,
        defaultBranch: String = "main",
        license: String? = "MIT",
        language: String? = "Kotlin",
        homepageUrl: String? = null,
        topics: String? = null,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime? = null,
        isDeleted: Boolean = false
    ): Repository {
        return Repository(
            id = id,
            owner = owner,
            name = name,
            description = description,
            visibilityCodeId = visibilityCodeId,
            defaultBranch = defaultBranch,
            license = license,
            language = language,
            homepageUrl = homepageUrl,
            topics = topics,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )
    }
}
