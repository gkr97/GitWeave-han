package com.example.gitserver.module.pullrequest.domain.event

import java.time.Instant

data class PullRequestCreated(
    val prId: Long,
    val repositoryId: Long,
    val baseCommit: String,
    val headCommit: String,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent