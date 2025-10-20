package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.RepositoryStats
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RepositoryStatsRepository: JpaRepository<RepositoryStats, Long> {

    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO repository_stats (repository_id, stars)
        VALUES (:repositoryId, 1)
        ON DUPLICATE KEY UPDATE stars = stars + 1
        """, nativeQuery = true
    )
    fun incrementStars(repositoryId: Long)

    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO repository_stats (repository_id, stars)
        VALUES (:repositoryId, 0)
        ON DUPLICATE KEY UPDATE stars = GREATEST(stars - 1, 0)
        """, nativeQuery = true
    )
    fun decrementStars(repositoryId: Long)

}