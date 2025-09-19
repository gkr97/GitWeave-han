package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.domain.PullRequestThread
import org.springframework.data.jpa.repository.JpaRepository

interface PullRequestThreadRepository : JpaRepository<PullRequestThread, Long> {
}