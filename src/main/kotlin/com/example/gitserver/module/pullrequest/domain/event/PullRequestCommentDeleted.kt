package com.example.gitserver.module.pullrequest.domain.event

import java.time.Instant

data class PullRequestCommentDeleted(
    val pullRequestId: Long,
    val commentId: Long,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent