package com.example.gitserver.module.pullrequest.domain

import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.repository.domain.Repository

@Entity
@Table(name = "pull_request")
data class PullRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    val repository: Repository,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    val author: User,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "status_code_id", nullable = false)
    var statusCodeId: Long,

    @Column(name = "merge_type_code_id")
    var mergeTypeCodeId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_by_id")
    var mergedBy: User? = null,

    @Column(name = "merged_at")
    var mergedAt: LocalDateTime? = null,

    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(name = "target_branch", nullable = false, length = 100)
    var targetBranch: String,

    @Column(name = "source_branch", nullable = false, length = 100)
    var sourceBranch: String,

    @Column(name = "base_commit_hash", length = 40)
    var baseCommitHash: String? = null,

    @Column(name = "head_commit_hash", length = 40)
    var headCommitHash: String? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L
) 