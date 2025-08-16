package com.example.gitserver.module.repository.application.command.service

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.application.service.GitService
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BranchService(
    private val branchRepository: BranchRepository,
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val gitService: GitService
) {
    /**
     * 브랜치 삭제합니다.
     * @param repositoryId 저장소 ID
     * @return 브랜치 목록
     */
    @Transactional
    fun deleteBranch(repositoryId: Long, branchName: String, requesterId: Long) {
        val repo = repositoryRepository.findById(repositoryId)
            .orElseThrow { RepositoryNotFoundException(repositoryId) }

        val isOwner = repo.owner.id == requesterId
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repositoryId, requesterId)
        if (!isOwner && !isCollaborator) {
            throw RepositoryAccessDeniedException(repositoryId, requesterId)
        }

        val incomingShort = GitRefUtils.toShortName(GitRefUtils.toFullRef(branchName))!!
        if (repo.defaultBranch == incomingShort) {
            throw DefaultBranchDeletionNotAllowedException(repositoryId, incomingShort)
        }

        val fullRef = GitRefUtils.toFullRef(branchName)
        val branch = branchRepository.findByRepositoryIdAndName(repositoryId, fullRef)
            ?: throw BranchNotFoundException(repositoryId, incomingShort)

        branchRepository.delete(branch)

        try {
            gitService.deleteBranch(repo, branchName)
        } catch (e: Exception) {
            throw GitBranchOperationFailedException("삭제", repositoryId, branchName, e)
        }
    }


    /**
     * 새로운 브랜치를 생성합니다.
     * @param repositoryId 저장소 ID
     * @param branchName 생성할 브랜치 이름
     * @param sourceBranch 기준 브랜치 이름 (null이면 기본 브랜치 사용)
     * @param requesterId 요청자 ID
     * @return 생성된 브랜치 ID
     */
    @Transactional
    fun createBranch(repositoryId: Long, branchName: String, sourceBranch: String?, requesterId: Long): Long {
        val repo = repositoryRepository.findById(repositoryId)
            .orElseThrow { RepositoryNotFoundException(repositoryId) }

        val isOwner = repo.owner.id == requesterId
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repositoryId, requesterId)
        if (!isOwner && !isCollaborator) {
            throw RepositoryAccessDeniedException(repositoryId, requesterId)
        }

        val fullRef = GitRefUtils.toFullRef(branchName)
        if (branchRepository.existsByRepositoryIdAndName(repositoryId, fullRef)) {
            throw BranchAlreadyExistsException(repositoryId, branchName)
        }

        val baseShort = sourceBranch ?: repo.defaultBranch
        val baseFull = GitRefUtils.toFullRef(baseShort)
        val srcBranch = branchRepository.findByRepositoryIdAndName(repositoryId, baseFull)
            ?: throw BaseBranchNotFoundException(repositoryId, baseShort)

        try {
            gitService.createBranch(repo, branchName, baseShort)
        } catch (e: Exception) {
            throw GitBranchOperationFailedException("생성", repositoryId, branchName, e)
        }

        val branch = Branch(
            repository = repo,
            name = fullRef,
            creator = repo.owner,
            headCommitHash = srcBranch.headCommitHash,
            lastCommitAt = srcBranch.lastCommitAt,
            isProtected = false,
            protectionRule = null,
            isDefault = false
        )
        branchRepository.save(branch)
        return branch.id
    }
}
