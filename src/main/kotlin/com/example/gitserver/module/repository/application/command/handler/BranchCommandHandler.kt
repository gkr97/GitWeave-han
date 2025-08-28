package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.repository.application.command.CreateBranchCommand
import com.example.gitserver.module.repository.application.command.DeleteBranchCommand
import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BranchCommandHandler(
    private val branchRepository: BranchRepository,
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val gitRepositoryPort: GitRepositoryPort,
) {

    @Transactional
    fun handle(cmd: DeleteBranchCommand) {
        val repo = repositoryRepository.findById(cmd.repositoryId)
            .orElseThrow { RepositoryNotFoundException(cmd.repositoryId) }

        val isOwner = repo.owner.id == cmd.requesterId
        val isCollaborator = collaboratorRepository
            .existsByRepositoryIdAndUserId(cmd.repositoryId, cmd.requesterId)
        if (!isOwner && !isCollaborator) {
            throw RepositoryAccessDeniedException(cmd.repositoryId, cmd.requesterId)
        }

        val incomingShort = GitRefUtils.toShortName(GitRefUtils.toFullRef(cmd.branchName))!!
        if (repo.defaultBranch == incomingShort) {
            throw DefaultBranchDeletionNotAllowedException(cmd.repositoryId, incomingShort)
        }

        val fullRef = GitRefUtils.toFullRef(cmd.branchName)
        val branch = branchRepository.findByRepositoryIdAndName(cmd.repositoryId, fullRef)
            ?: throw BranchNotFoundException(cmd.repositoryId, incomingShort)

        branchRepository.delete(branch)

        try {
            gitRepositoryPort.deleteBranch(repo, cmd.branchName)
        } catch (e: Exception) {
            throw GitBranchOperationFailedException("삭제", cmd.repositoryId, cmd.branchName, e)
        }
    }

    /**
     * 새로운 브랜치를 생성합니다.
     * - 요청자가 저장소 소유자이거나 협업자인지 확인
     * - 브랜치 이름 중복 검사
     * - 기준 브랜치 존재 여부 확인
     * - Git 레포지토리에 브랜치 생성
     * - DB에 브랜치 정보 저장
     *
     * @param cmd 생성할 브랜치 정보가 담긴 명령 객체
     * @return 생성된 브랜치의 ID
     * @throws RepositoryNotFoundException 저장소가 존재하지 않는 경우
     * @throws RepositoryAccessDeniedException 요청자가 저장소에 접근 권한이 없는 경우
     * @throws BranchAlreadyExistsException 동일한 이름의 브랜치가 이미 존재하는 경우
     * @throws BaseBranchNotFoundException 기준 브랜치가 존재하지 않는 경우
     * @throws GitBranchOperationFailedException Git 레포지토리에서 브랜치 생성에 실패한 경우
     */
    @Transactional
    fun handle(cmd: CreateBranchCommand): Long {
        val repo = repositoryRepository.findById(cmd.repositoryId)
            .orElseThrow { RepositoryNotFoundException(cmd.repositoryId) }

        val isOwner = repo.owner.id == cmd.requesterId
        val isCollaborator = collaboratorRepository
            .existsByRepositoryIdAndUserId(cmd.repositoryId, cmd.requesterId)
        if (!isOwner && !isCollaborator) {
            throw RepositoryAccessDeniedException(cmd.repositoryId, cmd.requesterId)
        }

        val fullRef = GitRefUtils.toFullRef(cmd.branchName)
        if (branchRepository.existsByRepositoryIdAndName(cmd.repositoryId, fullRef)) {
            throw BranchAlreadyExistsException(cmd.repositoryId, cmd.branchName)
        }

        val baseShort = cmd.sourceBranch ?: repo.defaultBranch
        val baseFull = GitRefUtils.toFullRef(baseShort)
        val srcBranch = branchRepository.findByRepositoryIdAndName(cmd.repositoryId, baseFull)
            ?: throw BaseBranchNotFoundException(cmd.repositoryId, baseShort)

        try {
            gitRepositoryPort.createBranch(repo, cmd.branchName, baseShort)
        } catch (e: Exception) {
            throw GitBranchOperationFailedException("생성", cmd.repositoryId, cmd.branchName, e)
        }

        val newBranch = Branch(
            repository = repo,
            name = fullRef,
            creator = repo.owner,
            headCommitHash = srcBranch.headCommitHash,
            lastCommitAt = srcBranch.lastCommitAt,
            isProtected = false,
            protectionRule = null,
            isDefault = false
        )
        branchRepository.save(newBranch)
        return newBranch.id
    }
}
