package com.example.gitserver.module.repository.domain

import com.example.gitserver.module.user.domain.User
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    val creator: User? = null,

    @Column(name = "head_commit_hash", length = 40)
    var headCommitHash: String? = null,

    @Column(name = "last_commit_at")
    var lastCommitAt: LocalDateTime? = null,

    @Column(name = "is_protected", nullable = false)
    var isProtected: Boolean = false,

    @Column(name = "protection_rule", columnDefinition = "TEXT")
    var protectionRule: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

) {

    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }

    fun updateHeadCommitHash(commitHash: String) {
        this.headCommitHash = commitHash
    }

    companion object {
        fun createDefault(repository: Repository, branchName: String, creator: User): Branch {
            return Branch(
                repository = repository,
                name = branchName,
                isDefault = true,
                creator = creator
            )
        }

    }
}