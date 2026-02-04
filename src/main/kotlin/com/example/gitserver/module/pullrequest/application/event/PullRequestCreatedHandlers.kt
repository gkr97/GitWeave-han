package com.example.gitserver.module.pullrequest.application.event

import com.example.gitserver.module.pullrequest.application.service.PullRequestCommitMappingService
import com.example.gitserver.module.pullrequest.application.service.PullRequestIndexingService
import com.example.gitserver.module.pullrequest.domain.event.PullRequestCreated
import com.example.gitserver.common.util.LogContext
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
        LogContext.with(
            "eventType" to "PR_CREATED",
            "repoId" to e.repositoryId.toString(),
            "prId" to e.prId.toString()
        ) {
            log.info { "[PR][Event] reindex + mapping" }
            indexing.reindex(e.prId, e.repositoryId, e.baseCommit, e.headCommit)
            mapping.refresh(e.prId, e.repositoryId, e.baseCommit, e.headCommit)
        }
    }
}
