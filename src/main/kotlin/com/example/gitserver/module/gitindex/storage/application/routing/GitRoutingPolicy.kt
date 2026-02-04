package com.example.gitserver.module.gitindex.storage.application.routing

import org.springframework.stereotype.Component

@Component
class GitRoutingPolicy {
    data class ReplicaState(
        val nodeId: String,
        val health: String,
        val lagMs: Long,
        val lagCommits: Long
    )

    fun selectReplica(
        replicas: List<ReplicaState>,
        useCommitLag: Boolean,
        maxLagMs: Long,
        maxLagCommits: Long
    ): ReplicaState? {
        val threshold = if (useCommitLag) maxLagCommits else maxLagMs
        return replicas
            .asSequence()
            .filter { it.health == "healthy" }
            .map { r ->
                val metric = if (useCommitLag) r.lagCommits else r.lagMs
                r to metric
            }
            .filter { (_, metric) -> metric <= threshold }
            .minByOrNull { (_, metric) -> metric }
            ?.first
    }
}
