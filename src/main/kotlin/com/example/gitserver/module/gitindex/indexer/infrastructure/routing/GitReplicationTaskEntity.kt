package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "git_replication_task")
data class GitReplicationTaskEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "repo_id", nullable = false)
    var repoId: Long,

    @Column(name = "source_node_id", nullable = false, length = 64)
    var sourceNodeId: String,

    @Column(name = "target_node_id", nullable = false, length = 64)
    var targetNodeId: String,

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "priority", nullable = false)
    var priority: Int = 0,

    @Column(name = "attempt", nullable = false)
    var attempt: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        updatedAt = LocalDateTime.now()
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
