package com.example.gitserver.module.gitindex.storage.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "git_routing_audit")
data class GitRoutingAuditEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "actor", length = 128)
    var actor: String? = null,

    @Column(name = "action", nullable = false, length = 64)
    var action: String,

    @Column(name = "repo_id")
    var repoId: Long? = null,

    @Column(name = "node_id", length = 64)
    var nodeId: String? = null,

    @Column(name = "payload", columnDefinition = "TEXT")
    var payload: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
