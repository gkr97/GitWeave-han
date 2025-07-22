package com.example.gitserver.module.pullrequest.domain

import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(name = "pull_request_reviewer")
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
    var reviewedAt: LocalDateTime? = null
) 