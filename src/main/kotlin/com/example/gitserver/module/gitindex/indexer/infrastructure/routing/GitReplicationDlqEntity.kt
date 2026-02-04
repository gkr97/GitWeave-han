package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "git_replication_dlq")
data class GitReplicationDlqEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "task_id", nullable = false)
    val taskId: Long,

    @Column(name = "repo_id", nullable = false)
    val repoId: Long,

    @Column(name = "source_node_id", nullable = false, length = 64)
    val sourceNodeId: String,

    @Column(name = "target_node_id", nullable = false, length = 64)
    val targetNodeId: String,

    @Column(name = "attempt", nullable = false)
    val attempt: Int,

    @Column(name = "last_error", columnDefinition = "TEXT")
    val lastError: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
