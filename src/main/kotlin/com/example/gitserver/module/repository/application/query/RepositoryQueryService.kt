package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.domain.service.CommitService
import com.example.gitserver.module.gitindex.domain.service.ReadmeService
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.application.service.GitService
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.*
import com.example.gitserver.module.repository.interfaces.dto.*
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class RepositoryQueryService(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val branchRepository: BranchRepository,
    private val repositoryStatsRepository: RepositoryStatsRepository,
    private val gitService: GitService,
    private val commitService: CommitService,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val readmeService: ReadmeService,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getRepository(repoId: Long, branch: String? = null): RepoDetailResponse {
        log.info { "[getRepository] repoId=$repoId, branch=${branch ?: "default"} 조회 시작" }

        val repo = repositoryRepository.findByIdWithOwner(repoId)
            ?: throw RepositoryNotFoundException(repoId)
        log.debug { "[getRepository] Repository: ${repo.name}, OwnerId=${repo.owner.id}" }

        val targetBranchName = branch ?: repo.defaultBranch
        log.debug { "[getRepository] 사용할 브랜치: $targetBranchName" }

        val mainBranchEntity = branchRepository.findByRepositoryIdAndName(repoId, targetBranchName)
            ?: throw RepositoryNotFoundException(repoId)
        log.debug { "[getRepository] 브랜치 엔티티 조회 완료: ${mainBranchEntity.name}" }

        val rawMainCommit = commitService.getLatestCommitHash(repoId, targetBranchName)
            ?: throw RepositoryNotFoundException(repoId)
        val mainBranchCommit = rawMainCommit.copy(author = enrichAuthor(rawMainCommit.author))
        log.debug { "[getRepository] 최신 커밋 조회 완료: ${mainBranchCommit.hash}" }

        val owner = RepositoryUserResponse(
            userId = repo.owner.id,
            nickname = repo.owner.name ?: "이름 없음",
            profileImageUrl = repo.owner.profileImageUrl
        )

        val contributors = collaboratorRepository.findAllAcceptedByRepositoryId(repoId).map {
            RepositoryUserResponse(
                userId = it.user.id,
                nickname = it.user.name ?: "알 수 없음",
                profileImageUrl = it.user.profileImageUrl
            )
        }
        log.debug { "[getRepository] 컨트리뷰터 수: ${contributors.size}" }

        val openPrCount = pullRequestRepository.countByRepositoryIdAndClosedFalse(repoId)
        log.debug { "[getRepository] 열린 PR 수: $openPrCount" }

        val languageStats = readmeService.getLanguageStats(repoId)
        val cloneUrls = gitService.getCloneUrls(repoId)
        val readmeInfo = readmeService.getReadmeInfo(repoId, mainBranchCommit.hash)
        val readmeContent = readmeService.getReadmeContent(repoId, mainBranchCommit.hash)
        val readmeHtml = readmeService.getReadmeHtml(repoId, mainBranchCommit.hash)

        val readme = ReadmeResponse(
            exists = readmeInfo.exists,
            path = readmeInfo.path,
            content = readmeContent,
            html = readmeHtml
        )
        log.debug { "[getRepository] README 존재 여부: ${readme.exists}, 경로: ${readme.path}" }

        val branches = branchRepository.findAllByRepositoryId(repoId).map {
            val rawHeadCommit = commitService.getLatestCommitHash(repoId, it.name)
                ?: throw RepositoryNotFoundException(repoId)
            val enrichedHeadCommit = rawHeadCommit.copy(author = enrichAuthor(rawHeadCommit.author))

            BranchResponse(
                name = it.name,
                isDefault = it.name == repo.defaultBranch,
                isProtected = it.isProtected,
                createdAt = it.createdAt,
                headCommit = enrichedHeadCommit
            )
        }
        log.debug { "[getRepository] 전체 브랜치 수: ${branches.size}" }

        val stats = repositoryStatsRepository.findById(repoId).orElse(null)
        val statsResponse = stats?.let {
            RepositoryStatsResponse(
                stars = it.stars,
                forks = it.forks,
                watchers = it.watchers,
                issues = it.issues,
                pullRequests = it.pullRequests,
                lastCommitAt = it.lastCommitAt
            )
        } ?: RepositoryStatsResponse(0, 0, 0, 0, 0, null)
        log.debug { "[getRepository] Stats: $statsResponse" }

        val visibility = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.id == repo.visibilityCodeId }?.code ?: "unknown"
        log.debug { "[getRepository] Visibility: $visibility" }

        val fileTree = commitService.getFileTreeAtRoot(repoId, mainBranchCommit.hash)
        log.debug { "[getRepository] 파일 트리 항목 수: ${fileTree.size}" }

        log.info { "[getRepository] repoId=$repoId 응답 구성 완료" }

        return RepoDetailResponse(
            id = repo.id,
            name = repo.name,
            description = repo.description,
            visibility = visibility,
            createdAt = repo.createdAt,
            lastUpdatedAt = repo.updatedAt,
            owner = owner,
            isOwner = false,
            isBookmarked = false,
            isInvited = false,
            contributors = contributors,
            openPrCount = openPrCount,
            languageStats = languageStats,
            cloneUrls = cloneUrls,
            readme = readme,
            defaultBranch = repo.defaultBranch,
            branches = branches,
            stats = statsResponse,
            fileTree = fileTree
        )
    }


    fun isOwner(repoId: Long, userId: Long?): Boolean {
        if (userId == null) return false
        val result = repositoryRepository.findByIdWithOwner(repoId)?.owner?.id == userId
        log.debug { "[isOwner] repoId=$repoId, userId=$userId -> $result" }
        return result
    }

    fun isBookmarked(repoId: Long, userId: Long?): Boolean {
        val result = userId?.let {
            bookmarkRepository.existsByUserIdAndRepositoryId(it, repoId)
        } ?: false
        log.debug { "[isBookmarked] repoId=$repoId, userId=$userId -> $result" }
        return result
    }

    fun isInvited(repoId: Long, userId: Long?): Boolean {
        val result = userId?.let {
            collaboratorRepository.existsByRepositoryIdAndUserId(repoId, it)
        } ?: false
        log.debug { "[isInvited] repoId=$repoId, userId=$userId -> $result" }
        return result
    }

    private fun enrichAuthor(user: RepositoryUserResponse): RepositoryUserResponse {
        val userEntity = userRepository.findById(user.userId).orElse(null)
        return userEntity?.let {
            RepositoryUserResponse(
                userId = it.id,
                nickname = it.name ?: "알 수 없음",
                profileImageUrl = it.profileImageUrl
            )
        } ?: user
    }
}
