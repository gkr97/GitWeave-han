package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.Repository
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RepositoryRepository : JpaRepository<Repository, Long> {
    fun existsByOwnerIdAndNameAndIsDeletedFalse(ownerId: Long, name: String): Boolean

    @Query("SELECT r FROM Repository r JOIN FETCH r.owner WHERE r.id = :id AND r.isDeleted = false")
    fun findByIdWithOwner(@Param("id") id: Long): Repository?

    fun findByOwnerIdAndIsDeletedFalse(ownerId: Long, pageable: Pageable): List<Repository>

    fun findByOwnerIdAndNameAndIsDeletedFalse(ownerId: Long, repoName: String): Repository?

    fun findByIdAndIsDeletedFalse(id: Long): Repository?

    @Query("SELECT r FROM Repository r WHERE r.owner.id = :ownerId AND r.visibilityCodeId = :visibilityCodeId AND r.isDeleted = false")
    fun findByOwnerIdAndVisibilityCodeIdAndIsDeletedFalse(ownerId: Long, visibilityCodeId: Long): List<Repository>

    fun existsByOwnerIdAndNameAndIdNot(ownerId: Long, name: String, exceptId: Long): Boolean
}




