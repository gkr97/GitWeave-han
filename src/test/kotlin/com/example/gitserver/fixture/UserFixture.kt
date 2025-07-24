package com.example.gitserver.fixture

import com.example.gitserver.module.user.domain.User
import java.time.LocalDateTime

object UserFixture {
    fun default(
        id: Long = 1L,
        email: String = "test@test.com",
        name: String? = "테스트유저",
        emailVerified: Boolean = false,
        isDeleted: Boolean = false
    ): User {
        return User(
            id = id,
            email = email,
            passwordHash = "hashed",
            name = name,
            profileImageUrl = null,
            bio = null,
            websiteUrl = null,
            timezone = null,
            emailVerified = emailVerified,
            isActive = !isDeleted,
            isDeleted = isDeleted,
            createdAt = LocalDateTime.now()
        )
    }
}

