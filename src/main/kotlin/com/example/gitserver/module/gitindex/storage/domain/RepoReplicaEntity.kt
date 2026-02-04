package com.example.gitserver.module.gitindex.storage.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

@Embeddable
data class RepoReplicaId(
    @Column(name = "repo_id", nullable = false)
    var repoId: Long = 0L,

    @Column(name = "node_id", nullable = false, length = 64)
    var nodeId: String = ""
)

@Entity
@Table(name = "repo_replica")
data class RepoReplicaEntity(
    @EmbeddedId
    var id: RepoReplicaId,

    @Column(name = "health", length = 32)
    var health: String? = null,

    @Column(name = "lag_ms")
    var lagMs: Long? = null,

    @Column(name = "lag_commits")
    var lagCommits: Long? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
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
