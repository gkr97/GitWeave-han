package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import org.springframework.data.jpa.repository.JpaRepository

interface GitReplicationDlqRepository : JpaRepository<GitReplicationDlqEntity, Long> {
    fun existsByTaskId(taskId: Long): Boolean
}
