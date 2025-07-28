package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.Repository
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RepositoryRepository : JpaRepository<Repository, Long> {
    fun existsByOwnerIdAndName(ownerId: Long, name: String): Boolean
    @Query("SELECT r FROM Repository r JOIN FETCH r.owner WHERE r.id = :id")
    fun findByIdWithOwner(@Param("id") id: Long): Repository?

    fun findByOwnerId(ownerId: Long, pageable: Pageable): List<Repository>

    fun findByIdIn(ids: List<Long>, pageable: Pageable): Page<Repository>

    fun findByOwnerIdAndName(ownerId: Long, repoName: String): Repository?

    @Query("""
    SELECT r FROM Repository r
    JOIN FETCH r.owner
    WHERE r.id IN :ids
      AND (
        (:keyword IS NULL OR :keyword = '') OR
        (LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
         LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
      )
""")
    fun findByIdInWithKeyword(
        @Param("ids") ids: List<Long>,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Repository>
}