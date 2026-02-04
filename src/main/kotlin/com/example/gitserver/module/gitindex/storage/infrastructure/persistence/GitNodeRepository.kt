package com.example.gitserver.module.gitindex.storage.infrastructure.persistence

import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GitNodeRepository : JpaRepository<GitNodeEntity, String>
