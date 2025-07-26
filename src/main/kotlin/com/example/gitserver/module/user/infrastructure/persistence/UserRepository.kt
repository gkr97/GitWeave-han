package com.example.gitserver.module.user.infrastructure.persistence

import com.example.gitserver.module.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository


interface UserRepository: JpaRepository<User, Long> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): User?
    fun findByIdOrIdNull(id: Long): User?
    fun findByEmailContainingOrNameContaining(keyword1: String, keyword2: String): List<User>
}