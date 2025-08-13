package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.Collaborator
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CollaboratorRepository: JpaRepository<Collaborator, Long> {
    fun findByRepositoryIdAndUserId(repositoryId: Long, userId: Long): Collaborator?

    fun deleteByRepositoryIdAndUserId(repositoryId: Long, userId: Long)

    fun findAllByRepositoryId(repoId: Long): List<Collaborator>

    fun existsByRepositoryIdAndUserId(repoId: Long, userId: Long) : Boolean
    @Query("SELECT c FROM Collaborator c JOIN FETCH c.user WHERE c.repository.id = :repoId")
    fun findAllWithUserByRepositoryId(@Param("repoId") repoId: Long): List<Collaborator>

    @Query("SELECT c FROM Collaborator c WHERE c.repository.id = :repoId AND c.accepted = true")
    fun findAllAcceptedByRepositoryId(@Param("repoId") repoId: Long): List<Collaborator>

    @Query("SELECT c FROM Collaborator c WHERE c.accepted = true AND c.user.id = :userId AND c.repository.id = :repoId")
    fun existsByRepositoryIdAndUserIdAndAcceptedTrue(repoId: Long, userId: Long): Boolean

    fun findAcceptedByUserId(userId: Long): List<Collaborator>

    @Query("""
    SELECT c FROM Collaborator c
    WHERE c.user.id = :userId
      AND c.repository.owner.id = :ownerId
      AND c.accepted = true
      AND c.repository.isDeleted = false
    """)
    fun findAcceptedByUserIdAndOwnerId(userId: Long, ownerId: Long): List<Collaborator>

}