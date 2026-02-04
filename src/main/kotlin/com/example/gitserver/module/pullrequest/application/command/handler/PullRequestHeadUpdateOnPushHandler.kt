package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.domain.event.PullRequestHeadSynced
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.gitindex.shared.domain.event.GitEvent
import com.example.gitserver.common.util.LogContext
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestHeadUpdateOnPushHandler(
    private val pullRequestRepository: PullRequestRepository,
    private val codes: CodeBook,
    private val events: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}

    /**
     * PR의 HEAD가 변경되었을 때, 해당 PR의 HEAD commit hash를 업데이트합니다.
    */
    @Transactional
    fun handle(event: GitEvent) {
        if (event.eventType != "PUSH") return
        val repoId = event.repositoryId
        val ref = event.branch?.let { GitRefUtils.toFullRef(it) } ?: return

        val openId = codes.prStatusId(PrStatus.OPEN)
        val prs = pullRequestRepository
            .findAllByRepositoryIdAndSourceBranchAndStatusCodeId(repoId, ref, openId)

        if (prs.isEmpty()) return

        prs.forEach { pr ->
            val oldHead = pr.headCommitHash
            val newHead = event.newrev ?: return@forEach
            if (oldHead == newHead) return@forEach

            pr.headCommitHash = newHead
            pullRequestRepository.save(pr)

            log.info { "[PR][HeadSync] pr=${pr.id} repo=$repoId head $oldHead -> $newHead" }
            LogContext.with(
                "eventType" to "PR_HEAD_SYNCED",
                "repoId" to repoId.toString(),
                "prId" to pr.id.toString(),
                "branch" to ref
            ) {
                events.publishEvent(PullRequestHeadSynced(pr.id, repoId, pr.baseCommitHash, newHead))
            }
        }
    }
}
