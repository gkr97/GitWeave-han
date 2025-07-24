package com.example.gitserver.module.user.infrastructure.persistence

import com.example.gitserver.module.user.domain.LoginHistory
import org.springframework.data.jpa.repository.JpaRepository

interface LoginHistoryRepository: JpaRepository<LoginHistory, Long> {

}