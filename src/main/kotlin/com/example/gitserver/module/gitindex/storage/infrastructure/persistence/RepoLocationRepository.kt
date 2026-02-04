package com.example.gitserver.module.gitindex.storage.infrastructure.persistence

import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RepoLocationRepository : JpaRepository<RepoLocationEntity, Long>
