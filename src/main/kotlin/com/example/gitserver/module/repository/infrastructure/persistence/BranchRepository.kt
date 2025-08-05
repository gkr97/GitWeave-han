package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.Branch
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BranchRepository : JpaRepository<Branch, Long>{
    fun findByRepositoryId(repositoryId: Long, pageable: Pageable): Page<Branch>

    fun findAllByRepositoryId(repositoryId: Long): List<Branch>

    fun findByRepositoryIdAndName(repositoryId: Long, name: String): Branch?

    fun existsByRepositoryIdAndIsDefaultIsTrue(repositoryId: Long): Boolean

    fun findByRepositoryIdAndNameContainingIgnoreCase(repositoryId: Long, keyword: String, pageable: Pageable): Page<Branch>

    fun findByRepositoryIdAndCreatorId(repositoryId: Long, creatorId: Long, pageable: Pageable): Page<Branch>

    fun findByRepositoryIdAndCreatorIdAndNameContainingIgnoreCase(repositoryId: Long, creatorId: Long, keyword: String, pageable: Pageable): Page<Branch>

    fun existsByRepositoryIdAndName(repositoryId: Long, branchName: String): Boolean
}