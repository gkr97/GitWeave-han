package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.Repository
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RepositoryRepository : JpaRepository<Repository, Long> {
    fun existsByOwnerIdAndName(ownerId: Long, name: String): Boolean
    @Query("SELECT r FROM Repository r JOIN FETCH r.owner WHERE r.id = :id")
    fun findByIdWithOwner(@Param("id") id: Long): Repository?
}