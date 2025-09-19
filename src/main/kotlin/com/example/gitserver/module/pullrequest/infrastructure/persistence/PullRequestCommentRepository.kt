package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.domain.PullRequestComment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PullRequestCommentRepository : JpaRepository<PullRequestComment, Long> {
    @Query(
        """
        select c from PullRequestComment c
        where c.pullRequest.id = :prId
        order by c.createdAt asc, c.id asc
        """
    )
    fun findAllByPullRequestIdOrderByCreated(prId: Long): List<PullRequestComment>
    fun findAllByPullRequestIdAndFilePathOrderByCreatedAt(prId: Long, filePath: String): List<PullRequestComment>
}