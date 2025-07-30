package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.domain.service.CommitService
import com.example.gitserver.module.gitindex.domain.service.ReadmeService
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.application.service.GitService
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.*
import com.example.gitserver.module.repository.interfaces.dto.*
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.StructuredTaskScope

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

    /**
     * 사용자 소속 저장소 목록을 조회합니다.
     * - 본인이 소유한 저장소
     * - 초대받은 저장소
     * - 북마크한 저장소
     *
     * @param userId 사용자 ID
     * @param request 페이지네이션 및 정렬 요청 정보
     * @return 사용자 소속 저장소 목록
     */
    @Transactional(readOnly = true)
    fun getRepositoryList(
        userId: Long,
        request: RepositoryListRequest
    ): MyRepositoriesResult {
        log.info { "[getRepositoryList] userId=$userId, request=$request 시작" }

        val sortProperty = when (request.sortBy) {
            "lastUpdatedAt", "updatedAt" -> "updatedAt"
            "name" -> "name"
            else -> "updatedAt"
        }
        val direction = if (request.sortDirection.equals("ASC", true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(request.page - 1, request.size, direction, sortProperty)
        log.debug { "[getRepositoryList] sortProperty=$sortProperty, direction=$direction, pageable=$pageable" }

        val ownIds = repositoryRepository.findByOwnerId(userId, Pageable.unpaged()).map { it.id }
        log.debug { "[getRepositoryList] ownIds(size=${ownIds.size})=$ownIds" }

        val invitedIds = collaboratorRepository.findAcceptedByUserId(userId).map { it.repository.id }
        log.debug { "[getRepositoryList] invitedIds(size=${invitedIds.size})=$invitedIds" }

        val bookmarkedIds = bookmarkRepository.findByUserId(userId).map { it.repository.id }
        log.debug { "[getRepositoryList] bookmarkedIds(size=${bookmarkedIds.size})=$bookmarkedIds" }

        val invitedSet = invitedIds.toSet()
        val filteredBookmarkedIds = repositoryRepository.findAllById(bookmarkedIds)
            .filter { repo ->
                val isPublic = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
                    .firstOrNull { it.id == repo.visibilityCodeId }?.code == "PUBLIC"
                isPublic || invitedSet.contains(repo.id)
            }
            .map { it.id }
        log.debug { "[getRepositoryList] filteredBookmarkedIds(size=${filteredBookmarkedIds.size})=$filteredBookmarkedIds" }

        val allIds = (ownIds + invitedIds + filteredBookmarkedIds).distinct()
        log.info { "[getRepositoryList] allIds(size=${allIds.size})=$allIds" }
        val page = if (!request.keyword.isNullOrBlank()) {
            repositoryRepository.findByIdInWithKeyword(allIds, request.keyword, pageable)
        } else {
            repositoryRepository.findByIdIn(allIds, pageable)
        }
        log.info { "[getRepositoryList] page number=${page.number + 1}, page size=${page.size}, totalElements=${page.totalElements}, totalPages=${page.totalPages}, contentSize=${page.content.size}" }

        val invitedSetFinal = invitedIds.toSet()
        val bookmarkedSetFinal = filteredBookmarkedIds.toSet()
        val ownSetFinal = ownIds.toSet()

        val content = page.content.map { repo ->
            val isOwner = ownSetFinal.contains(repo.id)
            val isBookmarked = bookmarkedSetFinal.contains(repo.id)
            val isInvited = invitedSetFinal.contains(repo.id)
            val visibility = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
                .firstOrNull { it.id == repo.visibilityCodeId }?.code ?: "unknown"
            RepositoryListItem(
                id = repo.id,
                name = repo.name,
                description = repo.description,
                visibility = visibility,
                lastUpdatedAt = repo.updatedAt.toString(),
                isOwner = isOwner,
                isBookmarked = isBookmarked,
                isInvited = isInvited,
                language = repo.language,
                ownerInfo = if (!isOwner) {
                    RepositoryUserResponse(
                        userId = repo.owner.id,
                        nickname = repo.owner.name ?: "이름 없음",
                        profileImageUrl = repo.owner.profileImageUrl
                    )
                } else null
            )
        }

        log.info { "[getRepositoryList] 최종 반환 content size=${content.size}" }

        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val profile = RepositoryUserResponse(
            userId = user.id,
            nickname = user.name ?: "",
            profileImageUrl = user.profileImageUrl
        )

        return MyRepositoriesResult(
            profile = profile,
            repositories = RepositoryListPageResponse(
                content = content,
                page = request.page,
                size = request.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                hasNext = page.hasNext()
            )
        )

    }


    /**
     * 저장소 상세 정보를 조회합니다.
     * - 저장소 소유자, 협업자, 북마크 여부 등 포함
     *
     * @param repoId 저장소 ID
     * @param branch 조회할 브랜치 이름 (기본값: null -> default 브랜치)
     * @param currentUserId 현재 사용자 ID (권한 체크용)
     * @return 저장소 상세 정보
     */
    @Transactional(readOnly = true)
    fun getRepository(repoId: Long, branch: String? = null, currentUserId: Long?): RepoDetailResponse {
        log.info { "[getRepository] repoId=$repoId, branch=${branch ?: "default"} 시작" }

        val repo = repositoryRepository.findByIdWithOwner(repoId)
            ?: throw RepositoryNotFoundException(repoId)

        val visibility = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.id == repo.visibilityCodeId }?.code ?: "unknown"

        if (visibility == "private") {
            val isOwner = repo.owner.id == currentUserId
            val isCollaborator = currentUserId?.let {
                collaboratorRepository.existsByRepositoryIdAndUserId(repoId, it)
            } ?: false
            if (!isOwner && !isCollaborator) {
                throw RepositoryAccessDeniedException(repoId, currentUserId)
            }
        }

        val targetBranchName = branch ?: repo.defaultBranch
        if (branchRepository.findByRepositoryIdAndName(repoId, targetBranchName) == null) {
            throw RepositoryNotFoundException(repoId)
        }

        val rawCommit = commitService.getLatestCommitHash(repoId, targetBranchName)
            ?: throw RepositoryNotFoundException(repoId)
        val mainCommit = rawCommit.copy(author = enrichAuthor(rawCommit.author))

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

        val openPrCount = pullRequestRepository.countByRepositoryIdAndClosedFalse(repoId)

        val readmeInfo: ReadmeResponse
        val readmeContent: String
        val readmeHtml: String
        val languageStats: List<LanguageStatResponse>
        val cloneUrls: CloneUrlsResponse
        val stats = repositoryStatsRepository.findById(repoId).orElse(null)

        StructuredTaskScope.ShutdownOnFailure().use { scope ->
            val readmeInfoFork = scope.fork {
                log.debug { "[Scope] readmeInfo 시작" }
                readmeService.getReadmeInfo(repoId, mainCommit.hash)
            }

            val readmeContentFork = scope.fork {
                log.debug { "[Scope] readmeContent 시작" }
                readmeService.getReadmeContent(repoId, mainCommit.hash)
            }

            val readmeHtmlFork = scope.fork {
                log.debug { "[Scope] readmeHtml 시작" }
                readmeService.getReadmeHtml(repoId, mainCommit.hash)
            }

            val languageStatsFork = scope.fork {
                log.debug { "[Scope] languageStats 시작" }
                readmeService.getLanguageStats(repoId)
            }

            val cloneUrlsFork = scope.fork {
                log.debug { "[Scope] cloneUrls 시작" }
                gitService.getCloneUrls(repo)
            }

            scope.join()
            scope.throwIfFailed()

            readmeInfo = readmeInfoFork.get()
            readmeContent = readmeContentFork.get().toString()
            readmeHtml = readmeHtmlFork.get().toString()
            languageStats = languageStatsFork.get()
            cloneUrls = cloneUrlsFork.get()
        }

        val readme = ReadmeResponse(
            exists = readmeInfo.exists,
            path = readmeInfo.path,
            content = readmeContent,
            html = readmeHtml
        )

        val branches = branchRepository.findAllByRepositoryId(repoId).map {
            val commit = commitService.getLatestCommitHash(repoId, it.name)
                ?: throw RepositoryNotFoundException(repoId)
            val enriched = commit.copy(author = enrichAuthor(commit.author))

            BranchResponse(
                name = it.name,
                isDefault = it.name == repo.defaultBranch,
                isProtected = it.isProtected,
                createdAt = it.createdAt,
                headCommit = enriched
            )
        }

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

        val fileTree = commitService.getFileTreeAtRoot(repoId, mainCommit.hash, targetBranchName)
        val sortedFileTree = fileTree.sortedWith(
            compareBy<TreeNodeResponse>({ !it.isDirectory }, { it.name.lowercase() })
        )

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
            fileTree = sortedFileTree
        )
    }

    fun isOwner(repoId: Long, userId: Long?): Boolean {
        return userId != null && repositoryRepository.findByIdWithOwner(repoId)?.owner?.id == userId
    }

    fun isBookmarked(repoId: Long, userId: Long?): Boolean {
        return userId?.let { bookmarkRepository.existsByUserIdAndRepositoryId(it, repoId) } ?: false
    }

    fun isInvited(repoId: Long, userId: Long?): Boolean {
        return userId?.let { collaboratorRepository.existsByRepositoryIdAndUserId(repoId, it) } ?: false
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
