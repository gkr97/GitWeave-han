package com.example.gitserver.module.pullrequest.domain.event

import java.time.Instant

data class PullRequestCommentCreated(
    val pullRequestId: Long,
    val commentId: Long,
    val commentType: String,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent
