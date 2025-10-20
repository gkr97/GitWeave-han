package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.RepositoryStar
import com.example.gitserver.module.repository.domain.RepositoryStarId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RepositoryStarRepository : JpaRepository<RepositoryStar, RepositoryStarId> {
    fun existsByUserIdAndRepositoryId(userId: Long, repositoryId: Long): Boolean
    fun deleteByUserIdAndRepositoryId(userId: Long, repositoryId: Long)
    fun findAllByRepositoryId(repositoryId: Long): List<RepositoryStar>
    fun countByRepositoryId(repositoryId: Long): Int
    fun findByUserId(userId: Long): List<RepositoryStar>
    
}