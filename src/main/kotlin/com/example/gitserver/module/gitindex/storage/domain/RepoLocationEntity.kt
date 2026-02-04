package com.example.gitserver.module.gitindex.storage.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "repo_location")
data class RepoLocationEntity(
    @Id
    @Column(name = "repo_id")
    var repoId: Long,

    @Column(name = "primary_node_id", nullable = false, length = 64)
    var primaryNodeId: String,

    @Column(name = "last_write_commit", length = 64)
    var lastWriteCommit: String? = null,

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
