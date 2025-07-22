package com.example.gitserver.module.pullrequest.domain

import jakarta.persistence.*

@Entity
@Table(name = "pull_request_file_diff")
data class PullRequestFileDiff(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @Column(name = "file_path", columnDefinition = "TEXT", nullable = false)
    var filePath: String,

    @Column(nullable = false)
    var additions: Int = 0,

    @Column(nullable = false)
    var deletions: Int = 0,

    @Column(columnDefinition = "TEXT")
    var patch: String? = null,

    @Column(name = "status_code_id", nullable = false)
    var statusCodeId: Long,

    @Column(name = "is_binary", nullable = false)
    var isBinary: Boolean = false
) 