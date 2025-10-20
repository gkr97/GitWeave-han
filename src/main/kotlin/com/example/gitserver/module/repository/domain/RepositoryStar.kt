package com.example.gitserver.module.repository.domain

import java.io.Serializable
import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(name = "repository_star")
@IdClass(RepositoryStarId::class)
data class RepositoryStar(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    val repository: Repository,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }
}

data class RepositoryStarId(
    val user: Long = 0,
    val repository: Long = 0
) : Serializable
