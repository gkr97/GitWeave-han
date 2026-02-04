package com.example.gitserver.module.gitindex.storage.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "git_node")
data class GitNodeEntity(
    @Id
    @Column(name = "node_id", length = 64)
    var nodeId: String,

    @Column(name = "host", nullable = false, length = 255)
    var host: String,

    @Column(name = "zone", length = 64)
    var zone: String? = null,

    @Column(name = "region", length = 64)
    var region: String? = null,

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "healthy",

    @Column(name = "disk_usage_pct", precision = 5, scale = 2)
    var diskUsagePct: BigDecimal? = null,

    @Column(name = "iops")
    var iops: Int? = null,

    @Column(name = "repo_count")
    var repoCount: Int? = null,

    @Column(name = "last_heartbeat_at")
    var lastHeartbeatAt: LocalDateTime? = null,

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
