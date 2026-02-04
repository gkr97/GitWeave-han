package com.example.gitserver.module.gitindex.storage.domain

data class GitRouteDecision(
    val repoId: Long,
    val nodeId: String,
    val host: String,
    val role: Role,
    val lagMs: Long? = null
) {
    enum class Role {
        PRIMARY,
        REPLICA,
        PRIMARY_FAILOVER
    }
}
