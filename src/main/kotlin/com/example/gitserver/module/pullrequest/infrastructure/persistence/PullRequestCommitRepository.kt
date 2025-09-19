package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.domain.PullRequestCommit
import com.example.gitserver.module.pullrequest.domain.PullRequestCommitId
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query


interface PullRequestCommitRepository : JpaRepository<PullRequestCommit, PullRequestCommitId> {

    @Modifying
    @Query("delete from PullRequestCommit c where c.pullRequest.id = :prId")
    fun deleteByPullRequestId(@Param("prId") prId: Long)

    @Query("select c.commitHash from PullRequestCommit c where c.pullRequest.id = :prId")
    fun findHashesByPullRequestId(@Param("prId") prId: Long): List<String>

}