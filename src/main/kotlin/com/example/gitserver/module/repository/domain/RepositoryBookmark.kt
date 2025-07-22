package com.example.gitserver.module.repository.domain

import java.io.Serializable
import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(name = "repository_bookmark")
@IdClass(RepositoryBookmarkId::class)
data class RepositoryBookmark(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    val repository: Repository,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// 복합키 클래스
data class RepositoryBookmarkId(
    val user: Long = 0,
    val repository: Long = 0
) : Serializable 