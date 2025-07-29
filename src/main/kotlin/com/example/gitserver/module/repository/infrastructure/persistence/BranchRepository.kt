package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.Branch
import org.springframework.data.jpa.repository.JpaRepository

interface BranchRepository : JpaRepository<Branch, Long>{
    fun findAllByRepositoryId(repositoryId: Long): List<Branch>
    fun findByRepositoryIdAndName(repositoryId: Long, name: String): Branch?
    fun existsByRepositoryIdAndIsDefaultIsTrue(repositoryId: Long): Boolean
}