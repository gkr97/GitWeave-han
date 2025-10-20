package com.example.gitserver.module.pullrequest.domain.event

import java.time.Instant

sealed interface DomainEvent {
    val occurredAt: Instant
}