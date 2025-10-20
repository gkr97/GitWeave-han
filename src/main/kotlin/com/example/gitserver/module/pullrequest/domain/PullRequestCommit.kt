package com.example.gitserver.module.pullrequest.domain

import java.io.Serializable
import jakarta.persistence.*

@Entity
@Table(name = "pull_request_commit")
@IdClass(PullRequestCommitId::class)
data class PullRequestCommit(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @Id
    @Column(name = "commit_hash", length = 40, nullable = false)
    val commitHash: String,

    @Column(name = "seq", nullable = false)
    val seq: Int
)

data class PullRequestCommitId(
    val pullRequest: Long = 0,
    val commitHash: String = ""
) : Serializable 