package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.common.cache.registerRepoCacheEvictionAfterCommit
import com.example.gitserver.module.gitindex.application.query.CommitQueryService
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.domain.event.SyncBranchEvent
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.domain.User
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

@Service
class GitRepositorySyncHandler(
    private val repositoryRepository: RepositoryRepository,
    private val branchRepository: BranchRepository,
    private val commitQuery: CommitQueryService,
    private val evictor: RepoCacheEvictor,
) {

    /**
     * 저장소 브랜치 동기화 이벤트를 처리합니다.
     * - 저장소 존재 여부 확인
     * - 브랜치 존재 여부에 따라 생성 또는 갱신
     * - 기본 브랜치 설정
     * - 커밋 시간 결정 (이벤트 제공 시간 우선, 없으면 쿼리 조회, 그래도 없으면 현재 시간)
     *
     * @param event 동기화 이벤트
     * @param creator 브랜치 생성자 정보
     */
    @Transactional
    fun handle(event: SyncBranchEvent, creator: User) {
        val took = measureTimeMillis {
            log.info { "[RepoSync] 시작 - repoId=${event.repositoryId}, branch=${event.branchName}, newHead=${event.newHeadCommit}, lastCommitAtUtc=${event.lastCommitAtUtc}" }

            val repo = repositoryRepository.findById(event.repositoryId)
                .orElseThrow {
                    log.error { "[RepoSync] 저장소 없음 - repoId=${event.repositoryId}" }
                    RepositoryNotFoundException(event.repositoryId)
                }

            val fullRef = GitRefUtils.toFullRef(event.branchName)
            val existing = branchRepository.findByRepositoryIdAndName(event.repositoryId, fullRef)

            if (event.newHeadCommit == null) {
                if (existing != null) {
                    branchRepository.delete(existing)
                    log.info { "[RepoSync] 브랜치 삭제 - repoId=${event.repositoryId}, ref=$fullRef" }
                } else {
                    log.warn { "[RepoSync] 브랜치 삭제 요청이나 기존 없음 - repoId=${event.repositoryId}, ref=$fullRef" }
                }
                return
            }

            val committedAt: LocalDateTime = when {
                event.lastCommitAtUtc != null -> {
                    log.debug { "[RepoSync] 커밋 시간 사용(이벤트 제공) - value=${event.lastCommitAtUtc}" }
                    event.lastCommitAtUtc
                }
                else -> {
                    val fromQuery = try {
                        commitQuery.getCommitInfo(event.repositoryId, event.newHeadCommit)
                    } catch (e: Exception) {
                        log.warn(e) { "[RepoSync] 커밋 정보 조회 실패 - repoId=${event.repositoryId}, commit=${event.newHeadCommit}. 현재 UTC 시간으로 대체" }
                        null
                    }

                    when (val ts = fromQuery?.committedAt) {
                        null -> {
                            val now = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime()
                            log.warn { "[RepoSync] 커밋 시간 미확인 - commit=${event.newHeadCommit}. 현재 UTC 시간으로 대체 - value=$now" }
                            now
                        }
                        else -> {
                            val dt = ts.atOffset(ZoneOffset.UTC).toLocalDateTime()
                            log.debug { "[RepoSync] 커밋 시간 사용(쿼리) - value=$dt" }
                            dt
                        }
                    }
                }
            }

            if (existing == null) {
                val existsDefault = branchRepository.existsByRepositoryIdAndIsDefaultIsTrue(event.repositoryId)
                val willBeDefault = !existsDefault
                val saved = branchRepository.save(
                    Branch(
                        repository = repo,
                        name = fullRef,
                        headCommitHash = event.newHeadCommit,
                        lastCommitAt = committedAt,
                        isDefault = willBeDefault,
                        creator = creator
                    )
                )
                log.info {
                    "[RepoSync] 브랜치 생성 - repoId=${event.repositoryId}, ref=$fullRef, head=${event.newHeadCommit}, " +
                            "lastCommitAt=$committedAt, default=$willBeDefault, id=${saved.id}"
                }
                if (willBeDefault) {
                    log.debug { "[RepoSync] 기본 브랜치로 설정 - repoId=${event.repositoryId}, ref=$fullRef" }
                }
            } else {
                val beforeHead = existing.headCommitHash
                existing.headCommitHash = event.newHeadCommit
                existing.lastCommitAt = committedAt
                branchRepository.save(existing)
                log.info {
                    "[RepoSync] 브랜치 갱신 - repoId=${event.repositoryId}, ref=$fullRef, head $beforeHead -> ${event.newHeadCommit}, lastCommitAt=$committedAt"
                }
            }
        }

        registerRepoCacheEvictionAfterCommit(
            evictor,
            evictDetailAndBranches = true,
            evictLists = true
        )

        log.info { "[RepoSync] 종료 - repoId=${event.repositoryId}, branch=${event.branchName}, tookMs=$took" }
    }
}
