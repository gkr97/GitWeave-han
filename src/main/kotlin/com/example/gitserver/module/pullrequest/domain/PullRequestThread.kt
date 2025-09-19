package com.example.gitserver.module.pullrequest.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "pull_request_thread")
data class PullRequestThread(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @Column(name = "file_path", length = 500, nullable = false)
    val filePath: String,

    @Column(name = "anchor", length = 255, nullable = false)
    val anchor: String,

    @Column(name = "resolved", nullable = false)
    var resolved: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
