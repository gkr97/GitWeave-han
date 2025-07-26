package com.example.gitserver.fixture

import com.example.gitserver.module.user.domain.User
import java.time.LocalDateTime

object UserFixture {
    fun default(
        id: Long = 1L,
        email: String = "test@test.com",
        name: String = "테스트유저",
        passwordHash: String = "encoded-password",
        emailVerified: Boolean = true,
        isActive: Boolean = true,
        isDeleted: Boolean = false
    ): User {
        return User(
            id = id,
            email = email,
            passwordHash = passwordHash,
            name = "테스트유저",
            profileImageUrl = null,
            bio = null,
            websiteUrl = null,
            timezone = null,
            emailVerified = emailVerified,
            isActive = isActive,
            isDeleted = isDeleted,
            createdAt = LocalDateTime.now()
        )
    }
}


