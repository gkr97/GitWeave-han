package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.repository.application.command.CreateBranchCommand
import com.example.gitserver.module.repository.application.command.DeleteBranchCommand
import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.common.cache.registerRepoCacheEvictionAfterCommit
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.domain.event.BranchCreated
import com.example.gitserver.module.repository.domain.event.BranchDeleted
import com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BranchCommandHandler(
    private val branchRepository: BranchRepository,
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositoryPort: GitRepositoryPort,
    private val evictor: RepoCacheEvictor,
    private val access: RepoAccessPolicy,
    private val events: ApplicationEventPublisher
) {

    /**
     * 저장소 브랜치를 삭제합니다.
     * - 요청자가 저장소 소유자인지 확인
     * - 기본 브랜치가 삭제될 수 없는지 확인
     * - 저장소에 해당하는 브랜치가 존재하는지 확인
     * - 저장소의 기본 브랜치가 삭제될 수 없는지 확인
     * - 저장소의 기본 브랜치가 삭제될 경우, 예외 발생
     * - 저장소의 기본 브랜치가 삭제될 경우, DB 정리
     * - 저장소의 기본 브랜치가 삭제될 경우, Git 정리
     * - 저장소의 기본 브랜치가 삭제될 경우, 이벤트 발행
     */
    @Transactional
    fun handle(cmd: DeleteBranchCommand) {
        val repo = repositoryRepository.findById(cmd.repositoryId)
            .orElseThrow { RepositoryNotFoundException(cmd.repositoryId) }

        if (!access.canWrite(repo.id, cmd.requesterId))
            throw RepositoryAccessDeniedException(repo.id, cmd.requesterId)

        val incomingShort = GitRefUtils.toShortName(GitRefUtils.toFullRef(cmd.branchName))!!
        if (repo.defaultBranch == incomingShort)
            throw DefaultBranchDeletionNotAllowedException(cmd.repositoryId, incomingShort)

        val fullRef = GitRefUtils.toFullRef(cmd.branchName)
        val branch = branchRepository.findByRepositoryIdAndName(cmd.repositoryId, fullRef)
            ?: throw BranchNotFoundException(cmd.repositoryId, incomingShort)

        // 1) Git 먼저
        try {
            gitRepositoryPort.deleteBranch(repo, cmd.branchName)
        } catch (e: Exception) {
            throw GitBranchOperationFailedException("삭제", cmd.repositoryId, cmd.branchName, e)
        }

        // 2) DB 정리
        branchRepository.delete(branch)

        // 3) 이벤트 발행
        events.publishEvent(BranchDeleted(repo.id, fullRef))

        registerRepoCacheEvictionAfterCommit(
            evictor,
            evictDetailAndBranches = true,
            evictLists = true
        )
    }

    /**
     * 저장소에 브랜치를 생성합니다.
     * - 요청자가 저장소 소유자인지 확인
     * - 동일 이름의 브랜치가 존재하는지 확인
     * - 소스 브랜치가 존재하는지 확인
     * - Git에 브랜치 생성
     * - DB에 브랜치 정보 저장
     * - 이벤트 발행
     */
    @Transactional
    fun handle(cmd: CreateBranchCommand): Long {
        val repo = repositoryRepository.findById(cmd.repositoryId)
            .orElseThrow { RepositoryNotFoundException(cmd.repositoryId) }

        if (!access.canWrite(repo.id, cmd.requesterId))
            throw RepositoryAccessDeniedException(repo.id, cmd.requesterId)

        val fullRef = GitRefUtils.toFullRef(cmd.branchName)
        if (branchRepository.existsByRepositoryIdAndName(cmd.repositoryId, fullRef))
            throw BranchAlreadyExistsException(cmd.repositoryId, cmd.branchName)

        val baseShort = cmd.sourceBranch ?: repo.defaultBranch
        val baseFull = GitRefUtils.toFullRef(baseShort)
        val srcBranch = branchRepository.findByRepositoryIdAndName(cmd.repositoryId, baseFull)
            ?: throw BaseBranchNotFoundException(cmd.repositoryId, baseShort)

        // 1) Git 생성
        try {
            gitRepositoryPort.createBranch(repo, cmd.branchName, baseShort)
        } catch (e: Exception) {
            throw GitBranchOperationFailedException("생성", cmd.repositoryId, cmd.branchName, e)
        }

        // 2) DB 반영
        val newBranch = Branch(
            repository = repo,
            name = fullRef,
            headCommitHash = srcBranch.headCommitHash,
            lastCommitAt = srcBranch.lastCommitAt,
            isDefault = false,
            creator = repo.owner,
            isProtected = false,
            protectionRule = null
        )
        val savedBranch = branchRepository.save(newBranch)

        // 3) 이벤트 발행
        events.publishEvent(BranchCreated(repo.id, fullRef, newBranch.headCommitHash))

        registerRepoCacheEvictionAfterCommit(
            evictor,
            evictDetailAndBranches = true,
            evictLists = true
        )
        return savedBranch.id
    }
}
