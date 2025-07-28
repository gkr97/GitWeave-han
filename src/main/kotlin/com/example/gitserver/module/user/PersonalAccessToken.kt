package com.example.gitserver.module.user

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "personal_access_token")
data class PersonalAccessToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "token_hash", nullable = false, length = 255)
    val tokenHash: String,

    @Column(name = "description", length = 255)
    val description: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
)
