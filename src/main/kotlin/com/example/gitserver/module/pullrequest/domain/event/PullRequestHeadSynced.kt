package com.example.gitserver.module.pullrequest.domain.event

import java.time.Instant

data class PullRequestHeadSynced(
    val pullRequestId: Long,
    val repositoryId: Long,
    val baseCommitHash: String?,
    val newHeadCommit: String,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent
