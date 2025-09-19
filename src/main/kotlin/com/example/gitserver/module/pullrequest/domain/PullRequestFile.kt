package com.example.gitserver.module.pullrequest.domain

import jakarta.persistence.*

@Entity
@Table(
    name = "pull_request_file",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_pr_file_prid_path", columnNames = ["pull_request_id", "path"])
    ]
)
data class PullRequestFile(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @Column(name = "path", length = 500, nullable = false)
    var path: String,

    @Column(name = "old_path", length = 500)
    var oldPath: String? = null,

    @Column(name = "status_code_id", nullable = false)
    var statusCodeId: Long,

    @Column(nullable = false)
    var additions: Int = 0,

    @Column(nullable = false)
    var deletions: Int = 0,

    @Column(name = "is_binary", nullable = false)
    var isBinary: Boolean = false,

    @Column(name = "blob_hash", length = 64)
    var blobHash: String? = null,

    @Column(name = "old_blob_hash", length = 64)
    var oldBlobHash: String? = null
)
