package com.example.gitserver.module.repository.domain

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "repository_stats")
data class RepositoryStats(

    @Id
    @Column(name = "repository_id")
    val id: Long? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    var repository: Repository,

    @Column(nullable = false)
    var stars: Int = 0,

    @Column(nullable = false)
    var forks: Int = 0,

    @Column(nullable = false)
    var watchers: Int = 0,

    @Column(nullable = false)
    var issues: Int = 0,

    @Column(name = "pull_requests", nullable = false)
    var pullRequests: Int = 0,

    @Column(name = "last_commit_at")
    var lastCommitAt: LocalDateTime? = null
) 