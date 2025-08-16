package com.example.gitserver.module.repository.application.command.service

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.application.service.CommitService
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class GitRepositorySyncService(
    private val repositoryRepository: RepositoryRepository,
    private val branchRepository: BranchRepository,
    private val commitService: CommitService,
) {
    /**
     * 브랜치 동기화
     * - 새로운 브랜치 생성 또는 기존 브랜치 업데이트
     * - 브랜치가 삭제된 경우 해당 브랜치 삭제
     * - 커밋 정보가 없으면 현재 시간으로 설정
     *
     * @param repositoryId 저장소 ID
     * @param branchName 브랜치 이름 (예: "refs/heads/main")
     * @param newHeadCommit 새 헤드 커밋 해시 (null이면 브랜치 삭제)
     * @param lastCommitAt 마지막 커밋 시간 (null이면 커밋 정보 조회)
     * @param creatorUser 브랜치를 생성한 사용자
     */
    @Transactional
    fun syncBranch(
        repositoryId: Long,
        branchName: String,
        newHeadCommit: String?,
        lastCommitAt: LocalDateTime?,
        creatorUser: User
    ) {
        val repo = repositoryRepository.findById(repositoryId)
            .orElseThrow { RepositoryNotFoundException(repositoryId) }

        val fullRef = GitRefUtils.toFullRef(branchName)
        val branch = branchRepository.findByRepositoryIdAndName(repositoryId, fullRef)

        if (newHeadCommit == null) {
            branch?.let { branchRepository.delete(it) }
            return
        }

        val committedAtLdt: LocalDateTime =
            lastCommitAt
                ?: commitService.getCommitInfo(repositoryId, newHeadCommit)
                    ?.committedAt
                    ?.atOffset(ZoneOffset.UTC)
                    ?.toLocalDateTime()
                ?: Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime()

        if (branch == null) {
            val existsDefault = branchRepository.existsByRepositoryIdAndIsDefaultIsTrue(repositoryId)
            branchRepository.save(
                Branch(
                    repository = repo,
                    name = fullRef,
                    headCommitHash = newHeadCommit,
                    lastCommitAt = committedAtLdt,
                    isDefault = !existsDefault,
                    creator = creatorUser
                )
            )
        } else {
            branch.headCommitHash = newHeadCommit
            branch.lastCommitAt = committedAtLdt
            branchRepository.save(branch)
        }
    }
}
