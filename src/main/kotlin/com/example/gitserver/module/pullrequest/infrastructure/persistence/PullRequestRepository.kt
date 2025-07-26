package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.domain.PullRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PullRequestRepository : JpaRepository<PullRequest, Long> {

    @Query("SELECT COUNT(pr) FROM PullRequest pr WHERE pr.repository.id = :repoId AND pr.statusCodeId = 2L")
    fun countByRepositoryIdAndClosedFalse(repoId: Long): Int
}