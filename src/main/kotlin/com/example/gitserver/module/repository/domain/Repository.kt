package com.example.gitserver.module.repository.domain

import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(name = "repository")
data class Repository(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "visibility_code_id")
    var visibilityCodeId: Long? = null,

    @Column(name = "default_branch", nullable = false, length = 100)
    var defaultBranch: String = "main",

    @Column(length = 100)
    var license: String? = null,

    @Column(length = 50)
    var language: String? = null,

    @Column(name = "homepage_url", length = 255)
    var homepageUrl: String? = null,

    @Column(columnDefinition = "TEXT")
    var topics: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
) {
    @PrePersist
    fun prePersist() {
        updatedAt = LocalDateTime.now()
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }

    companion object {
        fun create(
            owner: User,
            name: String,
            description: String?,
            visibilityCodeId: Long?,
            defaultBranch: String,
            license: String?,
            language: String?,
            homepageUrl: String?,
            topics: String?
        ): Repository {
            return Repository(
                owner = owner,
                name = name,
                description = description,
                visibilityCodeId = visibilityCodeId,
                defaultBranch = defaultBranch,
                license = license,
                language = language,
                homepageUrl = homepageUrl,
                topics = topics
            )
        }
    }
}