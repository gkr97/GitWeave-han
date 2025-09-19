package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.domain.PullRequestReviewer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PullRequestReviewerRepository : JpaRepository<PullRequestReviewer, Long> {
    fun existsByPullRequestIdAndReviewerId(prId: Long, reviewerId: Long): Boolean
    fun findByPullRequestIdAndReviewerId(prId: Long, reviewerId: Long): PullRequestReviewer?
    fun findAllByPullRequestId(prId: Long): List<PullRequestReviewer>

    @Query("""
        SELECT r.statusCodeId, COUNT(r.id) 
        FROM PullRequestReviewer r 
        WHERE r.pullRequest.id = :pullRequestId
        GROUP BY r.statusCodeId
    """)
    fun countByStatusGrouped(pullRequestId: Long): List<Array<Any>>
}