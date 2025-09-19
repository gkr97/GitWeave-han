package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.pullrequest.application.service.PullRequestCommitMappingService
import com.example.gitserver.module.pullrequest.application.service.PullRequestIndexingService
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestHeadUpdateOnPushHandler(
    private val pullRequestRepository: PullRequestRepository,
    private val codeCache: CommonCodeCacheService,
    private val indexingService: PullRequestIndexingService,
    private val commitMappingService: PullRequestCommitMappingService,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Git PUSH 이벤트 처리
     * - PUSH 이벤트가 아니면 무시
     * - 오픈된 PR 중 sourceBranch가 푸시된 ref와 일치하는 PR 조회
     * - PR headCommitHash 갱신
     * - baseCommitHash가 있으면 인덱싱/커밋매핑 갱신
     */
    @Transactional
    fun handle(event: GitEvent) {
        if (event.eventType != "PUSH") return
        val repoId = event.repositoryId
        val ref = event.branch?.let { GitRefUtils.toFullRef(it) } ?: return

        val openId = codeCache.getCodeDetailsOrLoad("PR_STATUS")
            .first { it.code.equals("open", true) }.id

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

            pr.baseCommitHash?.let { base ->
                indexingService.reindex(
                    prId = pr.id,
                    repoId = repoId,
                    base = base,
                    head = newHead
                )
                commitMappingService.refresh(
                    prId = pr.id,
                    repoId = repoId,
                    base = base,
                    head = newHead
                )
            }
        }
    }
}
