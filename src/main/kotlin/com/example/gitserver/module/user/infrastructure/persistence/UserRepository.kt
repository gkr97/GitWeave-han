package com.example.gitserver.module.user.infrastructure.persistence

import com.example.gitserver.module.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository: JpaRepository<User, Long> {
    fun existsByEmailAndIsDeletedFalse(email: String): Boolean

    fun findByEmailAndIsDeletedFalse(email: String): User?

    fun findByIdAndIsDeletedFalse(id: Long): User?

    fun findByEmailContainingOrNameContainingAndIsDeletedFalse(
        keyword1: String, keyword2: String
    ): List<User>

    fun findByNameAndIsDeletedFalse(name: String): User?
}
