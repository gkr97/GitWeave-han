package com.example.gitserver.module.user.infrastructure.persistence

import com.example.gitserver.module.user.PersonalAccessToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PersonalAccessTokenRepository : JpaRepository<PersonalAccessToken, Long> {

    @Query("SELECT pat FROM PersonalAccessToken pat WHERE pat.userId = :userId AND pat.isActive = true AND pat.tokenHash = :tokenHash")
    fun findByUserIdAndTokenHashAndIsActiveTrue(userId: Long, tokenHash: String): PersonalAccessToken?
}