package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.domain.PullRequestMergeLog
import org.springframework.data.jpa.repository.JpaRepository

interface PullRequestMergeLogRepository : JpaRepository<PullRequestMergeLog, Long>
