package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.application.service.CommitService
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.BranchListPageResponse
import com.example.gitserver.module.repository.interfaces.dto.BranchResponse
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import mu.KotlinLogging
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.ISO_INSTANT

@Service
class BranchQueryService(
    private val branchRepository: BranchRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val repositoryRepository: RepositoryRepository,
    private val commitService: CommitService,
    private val commonCodeCacheService: CommonCodeCacheService,
) {
    private val log = KotlinLogging.logger {}



    private fun String.toShortBranchName(): String = this.removePrefix("refs/heads/")

    @Transactional(readOnly = true)
    fun getBranchList(
        repositoryId: Long,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String,
        keyword: String?,
        onlyMine: Boolean,
        currentUserId: Long?
    ): BranchListPageResponse {
        log.info { "[getBranchList] repositoryId=$repositoryId, page=$page, size=$size, sortBy=$sortBy, sortDirection=$sortDirection, keyword=$keyword, onlyMine=$onlyMine, currentUserId=$currentUserId" }

        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        // 접근 검사
        val visibilityCodes = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
        val publicCodeId = visibilityCodes.firstOrNull { it.code.equals("PUBLIC", true) }?.id
        val isPublic = (repo.visibilityCodeId == publicCodeId)
        if (!isPublic) {
            val isOwner = repo.owner.id == currentUserId
            val isCollaborator = currentUserId != null &&
                    collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, currentUserId)
            if (!isOwner && !isCollaborator) {
                throw IllegalArgumentException("허용되지 않은 접근")
            }
        }

        val sortProperty = when (sortBy) {
            "NAME" -> "name"
            "LAST_COMMIT_AT" -> "lastCommitAt"
            else -> "name"
        }
        val direction = if (sortDirection.equals("ASC", true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), size.coerceAtLeast(1), direction, sortProperty)

        val pageResult = when {
            onlyMine && !keyword.isNullOrBlank() && currentUserId != null ->
                branchRepository.findByRepositoryIdAndCreatorIdAndNameContainingIgnoreCase(repositoryId, currentUserId, keyword, pageable)
            onlyMine && currentUserId != null ->
                branchRepository.findByRepositoryIdAndCreatorId(repositoryId, currentUserId, pageable)
            !keyword.isNullOrBlank() ->
                branchRepository.findByRepositoryIdAndNameContainingIgnoreCase(repositoryId, keyword, pageable)
            else ->
                branchRepository.findByRepositoryId(repositoryId, pageable)
        }

        val content = pageResult.content.map { branch ->
            val qualifiedName = branch.name
            val shortName = qualifiedName.toShortBranchName()

            // 1. head_commit_hash 로 조회
            val byHash = branch.headCommitHash?.let { commitHash ->
                commitService.getCommitInfo(repositoryId, commitHash)
            }

            // 2. branch 이름으로 최신 커밋 조회
            val byBranchLatest = if (byHash == null) {
                commitService.getLatestCommitHash(repositoryId, qualifiedName)
            } else null

            val commitDto: CommitResponse = (byHash ?: byBranchLatest) ?: run {
                CommitResponse(
                    hash = branch.headCommitHash ?: "",
                    message = "",
                    committedAt = (branch.lastCommitAt ?: branch.createdAt)
                        .atOffset(ZoneOffset.UTC)
                        .toLocalDateTime(),
                    author = RepositoryUserResponse(0L, "", null)
                )
            }

            val creatorDto = branch.creator?.let { user ->
                RepositoryUserResponse(user.id, user.name ?: "", user.profileImageUrl)
            }

            BranchResponse(
                name = shortName,
                qualifiedName = qualifiedName,
                isDefault = branch.isDefault,
                isProtected = branch.isProtected,
                createdAt = branch.createdAt
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDateTime(),
                headCommit = commitDto,
                creator = creatorDto
            )
        }


        return BranchListPageResponse(
            content = content,
            page = page,
            size = size,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages,
            hasNext = pageResult.hasNext()
        )
    }
}
