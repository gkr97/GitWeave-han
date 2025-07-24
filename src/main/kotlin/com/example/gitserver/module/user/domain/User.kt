package com.example.gitserver.module.user.domain

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "user")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, unique = true, length = 255)
    var email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Column(length = 100)
    var name: String? = null,

    @Column(name = "profile_image_url", length = 500)
    var profileImageUrl: String? = null,

    @Column(columnDefinition = "TEXT")
    var bio: String? = null,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "provider_code_id")
    var providerCodeId: Long? = null,

    @Column(name = "provider_id", length = 100)
    var providerId: String? = null,

    @Column(length = 50)
    var timezone: String? = null,

    @Column(length = 100)
    var location: String? = null,

    @Column(name = "website_url", length = 255)
    var websiteUrl: String? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

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

    fun verifyEmail() {
        if (emailVerified) throw IllegalStateException("이미 인증된 이메일입니다.")
        this.emailVerified = true
    }

    fun updateProfileImage(imageUrl: String) {
        this.profileImageUrl = imageUrl
    }
}