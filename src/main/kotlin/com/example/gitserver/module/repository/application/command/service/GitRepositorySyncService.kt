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
