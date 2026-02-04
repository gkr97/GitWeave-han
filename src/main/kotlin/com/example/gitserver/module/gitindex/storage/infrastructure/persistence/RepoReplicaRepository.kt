package com.example.gitserver.module.gitindex.storage.infrastructure.persistence

import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import org.springframework.data.jpa.repository.JpaRepository

interface RepoReplicaRepository : JpaRepository<RepoReplicaEntity, RepoReplicaId> {
    fun findByIdRepoId(repoId: Long): List<RepoReplicaEntity>
    fun findByIdRepoIdAndIdNodeId(repoId: Long, nodeId: String): RepoReplicaEntity?
    fun deleteByIdRepoIdAndIdNodeId(repoId: Long, nodeId: String)
    fun deleteByIdRepoId(repoId: Long)
}
