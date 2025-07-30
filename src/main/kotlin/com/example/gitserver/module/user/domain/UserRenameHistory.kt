package com.example.gitserver.module.user.domain

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "user_rename_history")
data class UserRenameHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "old_username", nullable = false, length = 100)
    val oldUsername: String,

    @Column(name = "new_username", nullable = false, length = 100)
    val newUsername: String,

    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime = LocalDateTime.now()
)
