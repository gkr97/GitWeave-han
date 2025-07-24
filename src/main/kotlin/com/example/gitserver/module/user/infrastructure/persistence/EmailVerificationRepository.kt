package com.example.gitserver.module.user.infrastructure.persistence

import com.example.gitserver.module.user.domain.EmailVerification
import org.springframework.data.jpa.repository.JpaRepository

interface EmailVerificationRepository : JpaRepository<EmailVerification, Long> {
    fun findByToken(token: String): EmailVerification?
}