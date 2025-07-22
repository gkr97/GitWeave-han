package com.example.gitserver.module.repository.domain

import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(name = "collaborator")
data class Collaborator(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    val repository: Repository,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "role_code_id", nullable = false)
    var roleCodeId: Long,

    @Column(name = "invited_at", nullable = false)
    val invitedAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var accepted: Boolean = false
) 