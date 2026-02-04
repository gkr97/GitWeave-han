package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface GitReplicationTaskRepository : JpaRepository<GitReplicationTaskEntity, Long> {
    fun findTop100ByStatusOrderByPriorityDescCreatedAtAsc(status: String): List<GitReplicationTaskEntity>
    fun findTop100ByStatusAndTargetNodeIdOrderByPriorityDescCreatedAtAsc(
        status: String,
        targetNodeId: String
    ): List<GitReplicationTaskEntity>
    fun existsByRepoIdAndTargetNodeIdAndStatusIn(repoId: Long, targetNodeId: String, status: List<String>): Boolean

    @Modifying
    @Transactional
    @Query("update GitReplicationTaskEntity t set t.status = :newStatus, t.updatedAt = CURRENT_TIMESTAMP where t.id = :id and t.status = :expectedStatus")
    fun claimTask(
        @Param("id") id: Long,
        @Param("expectedStatus") expectedStatus: String,
        @Param("newStatus") newStatus: String
    ): Int
}
