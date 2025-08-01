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

    fun findByIdInAndIsDeletedFalse(ids: List<Long>, pageable: Pageable): Page<Repository>

    fun findByOwnerIdAndNameAndIsDeletedFalse(ownerId: Long, repoName: String): Repository?

    @Query("""
    SELECT r FROM Repository r
    JOIN FETCH r.owner
    WHERE r.id IN :ids
      AND r.isDeleted = false
      AND (
        (:keyword IS NULL OR :keyword = '') OR
        (LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
         LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
      )
    """)
    fun findByIdInAndKeywordIgnoreCase(
        @Param("ids") ids: List<Long>,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Repository>

    @Query("SELECT r FROM Repository r WHERE r.owner.id = :ownerId AND r.visibilityCodeId = :visibilityCodeId AND r.isDeleted = false")
    fun findByOwnerIdAndVisibilityCodeIdAndIsDeletedFalse(ownerId: Long, visibilityCodeId: Long): List<Repository>
}




