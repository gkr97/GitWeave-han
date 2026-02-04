package com.example.gitserver.module.gitindex.storage.infrastructure.persistence

import com.example.gitserver.module.gitindex.storage.domain.GitRoutingAuditEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GitRoutingAuditRepository : JpaRepository<GitRoutingAuditEntity, Long>
