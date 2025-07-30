package com.example.gitserver.module.user.infrastructure.persistence

import com.example.gitserver.module.user.domain.UserRenameHistory
import org.springframework.data.jpa.repository.JpaRepository

interface UserRenameHistoryRepository: JpaRepository<UserRenameHistory, Long>

