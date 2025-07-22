package com.example.gitserver.module.pullrequest.domain

import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(name = "pull_request_merge_log")
data class PullRequestMergeLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_by_id", nullable = false)
    val mergedBy: User,

    @Column(name = "merge_commit_hash", length = 40, nullable = false)
    var mergeCommitHash: String,

    @Column(name = "merge_type_code_id")
    var mergeTypeCodeId: Long? = null,

    @Column(name = "merged_at", nullable = false)
    var mergedAt: LocalDateTime
) 