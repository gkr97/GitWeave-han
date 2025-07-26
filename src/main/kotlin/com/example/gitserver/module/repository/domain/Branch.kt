package com.example.gitserver.module.repository.domain

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "branch")
data class Branch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    val repository: Repository,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "head_commit_hash", length = 40)
    var headCommitHash: String? = null,

    @Column(name = "is_protected", nullable = false)
    var isProtected: Boolean = false,

    @Column(name = "protection_rule", columnDefinition = "TEXT")
    var protectionRule: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false
) {

    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }

    fun updateHeadCommitHash(commitHash: String) {
        this.headCommitHash = commitHash
    }

    companion object {
        fun createDefault(repository: Repository, branchName: String): Branch {
            return Branch(
                repository = repository,
                name = branchName,
                isDefault = true
            )
        }

    }
}