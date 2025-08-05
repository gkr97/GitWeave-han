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

@Service
class BranchQueryService(
    private val branchRepository: BranchRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val repositoryRepository: RepositoryRepository,
    private val commitService: CommitService,
    private val commonCodeCacheService: CommonCodeCacheService,
) {
    private val log = KotlinLogging.logger {}

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
        log.info { "[getBranchList] repositoryId=$repositoryId, page=$page, size=$size," +
                " sortBy=$sortBy, sortDirection=$sortDirection, keyword=$keyword, onlyMine=$onlyMine," +
                " currentUserId=$currentUserId" }

        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

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
            "LAST_COMMIT_AT" -> "headCommitHash"
            else -> "name"
        }
        val direction = if (sortDirection.equals("ASC", true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), size.coerceAtLeast(1), direction, sortProperty)

        val pageResult = when {
            onlyMine && !keyword.isNullOrBlank() && currentUserId != null -> {
                log.debug { "[getBranchList] OnlyMine+Keyword+CurrentUser -> findByRepositoryIdAndCreatorIdAndNameContainingIgnoreCase" }
                branchRepository.findByRepositoryIdAndCreatorIdAndNameContainingIgnoreCase(repositoryId, currentUserId, keyword, pageable)
            }
            onlyMine && currentUserId != null -> {
                log.debug { "[getBranchList] OnlyMine+CurrentUser -> findByRepositoryIdAndCreatorId" }
                branchRepository.findByRepositoryIdAndCreatorId(repositoryId, currentUserId, pageable)
            }
            !keyword.isNullOrBlank() -> {
                log.debug { "[getBranchList] KeywordOnly -> findByRepositoryIdAndNameContainingIgnoreCase" }
                branchRepository.findByRepositoryIdAndNameContainingIgnoreCase(repositoryId, keyword, pageable)
            }
            else -> {
                log.debug { "[getBranchList] All -> findByRepositoryId" }
                branchRepository.findByRepositoryId(repositoryId, pageable)
            }
        }

        val content = pageResult.content.map { branch ->
            log.debug { "[getBranchList] Branch: name=${branch.name}, headCommitHash=${branch.headCommitHash}, creatorId=${branch.creator?.id}" }

            val commitDto: CommitResponse = branch.headCommitHash?.let { commitHash ->
                commitService.getCommitInfo(repositoryId, commitHash)
            } ?: CommitResponse(
                hash = "",
                message = "",
                committedAt = branch.createdAt,
                author = RepositoryUserResponse(
                    userId = 0L,
                    nickname = "",
                    profileImageUrl = null
                )
            )

            val creatorDto = branch.creator?.let { user ->
                RepositoryUserResponse(
                    userId = user.id,
                    nickname = user.name ?: "",
                    profileImageUrl = user.profileImageUrl
                )
            } ?: RepositoryUserResponse(
                userId = 0L,
                nickname = "알 수 없음",
                profileImageUrl = null
            )

            BranchResponse(
                name = branch.name,
                isDefault = branch.isDefault,
                isProtected = branch.isProtected,
                createdAt = branch.createdAt,
                headCommit = commitDto,
                creator = creatorDto
            )
        }

        log.info { "[getBranchList] 반환 branch count=${content.size}, totalElements=${pageResult.totalElements}" }
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
