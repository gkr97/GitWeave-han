package com.example.gitserver.module.user.domain

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "login_history")
data class LoginHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "ip_address", nullable = false, length = 50)
    val ipAddress: String,

    @Column(name = "user_agent", columnDefinition = "TEXT")
    val userAgent: String? = null,

    @Column(name = "login_at", nullable = false)
    val loginAt: LocalDateTime,

    @Column(nullable = false)
    val success: Boolean = true
) 