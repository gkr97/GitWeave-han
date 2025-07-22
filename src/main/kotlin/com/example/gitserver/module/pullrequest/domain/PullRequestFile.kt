package com.example.gitserver.module.pullrequest.domain

import jakarta.persistence.*

@Entity
@Table(name = "pull_request_file")
data class PullRequestFile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @Column(columnDefinition = "TEXT", nullable = false)
    var path: String,

    @Column(name = "status_code_id", nullable = false)
    var statusCodeId: Long,

    @Column(nullable = false)
    var additions: Int = 0,

    @Column(nullable = false)
    var deletions: Int = 0
) 