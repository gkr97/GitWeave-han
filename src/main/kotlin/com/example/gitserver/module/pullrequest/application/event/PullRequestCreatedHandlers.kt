package com.example.gitserver.module.pullrequest.application.event

import com.example.gitserver.module.pullrequest.application.service.PullRequestCommitMappingService
import com.example.gitserver.module.pullrequest.application.service.PullRequestIndexingService
import com.example.gitserver.module.pullrequest.domain.event.PullRequestCreated
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PullRequestCreatedHandlers(
    private val indexing: PullRequestIndexingService,
    private val mapping: PullRequestCommitMappingService
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    @EventListener
    fun on(e: PullRequestCreated) {
        log.info { "[PR][Event] pr=${e.prId} reindex + mapping" }
        indexing.reindex(e.prId, e.repositoryId, e.baseCommit, e.headCommit)
        mapping.refresh(e.prId, e.repositoryId, e.baseCommit, e.headCommit)
    }
}
