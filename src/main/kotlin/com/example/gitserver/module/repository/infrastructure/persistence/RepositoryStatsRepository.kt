package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.RepositoryStats
import org.springframework.data.jpa.repository.JpaRepository

interface RepositoryStatsRepository: JpaRepository<RepositoryStats, Long> {

}