package com.example.gitserver.fixture

import com.example.gitserver.module.pullrequest.domain.PullRequest
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.user.domain.User
import java.time.LocalDateTime

object PullRequestFixture {
    fun default(
        id: Long = 1L,
        repository: Repository,
        author: User,
        title: String = "테스트 PR",
        description: String? = "테스트 설명",
        statusCodeId: Long = 1L,
        targetBranch: String = "refs/heads/main",
        sourceBranch: String = "refs/heads/feature",
        baseCommitHash: String? = "abc123",
        headCommitHash: String? = "def456",
        createdAt: LocalDateTime = LocalDateTime.now()
    ): PullRequest {
        return PullRequest(
            id = id,
            repository = repository,
            author = author,
            title = title,
            description = description,
            statusCodeId = statusCodeId,
            mergeTypeCodeId = null,
            mergedBy = null,
            mergedAt = null,
            closedAt = null,
            targetBranch = targetBranch,
            sourceBranch = sourceBranch,
            baseCommitHash = baseCommitHash,
            headCommitHash = headCommitHash,
            createdAt = createdAt
        )
    }
}
