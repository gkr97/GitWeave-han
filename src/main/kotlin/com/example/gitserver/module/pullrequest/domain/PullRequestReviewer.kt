package com.example.gitserver.module.pullrequest.domain

import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(
    name = "pull_request_reviewer",
    uniqueConstraints = [UniqueConstraint(columnNames = ["pull_request_id", "reviewer_id"])]
)
data class PullRequestReviewer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    val reviewer: User,

    @Column(name = "status_code_id", nullable = false)
    var statusCodeId: Long,

    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
)
