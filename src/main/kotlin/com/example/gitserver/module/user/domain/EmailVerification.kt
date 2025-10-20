package com.example.gitserver.module.user.domain

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "email_verification")
data class EmailVerification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 255)
    var token: String,

    @Column(name = "is_used", nullable = false)
    var isUsed: Boolean = false,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {

    fun markUsed() {
        if (isUsed) throw IllegalStateException("이미 사용된 토큰입니다.")
        this.isUsed = true
    }
    fun isExpired(now: LocalDateTime = LocalDateTime.now()): Boolean =
        expiresAt.isBefore(now)

    fun canBeUsed(now: LocalDateTime = LocalDateTime.now()): Boolean =
        !isUsed && !isExpired(now)
}