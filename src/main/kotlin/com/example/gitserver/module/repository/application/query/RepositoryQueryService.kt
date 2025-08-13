package com.example.gitserver.module.repository.application.query

import com.example.gitserver.common.pagination.CursorCodec
import com.example.gitserver.common.pagination.CursorPayload
import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PageInfoDTO
import com.example.gitserver.common.pagination.SortDirection as KeysetDir
import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.application.service.CommitService
import com.example.gitserver.module.gitindex.application.service.GitService
import com.example.gitserver.module.gitindex.application.service.ReadmeService
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.application.query.model.*
import com.example.gitserver.module.repository.exception.*
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
import java.time.ZoneOffset
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
    private val repositoryKeysetJdbcRepository: RepositoryKeySetRepository
) {

    /**
     * 저장소 단건 상세
     */
    @Transactional(readOnly = true)
    fun getRepository(repoId: Long, branch: String? = null, currentUserId: Long?): RepoDetailResponse {
        log.info { "[getRepository] repoId=$repoId, branch=${branch ?: "default"} 시작" }

        val repo = repositoryRepository.findByIdWithOwner(repoId)
            ?: throw RepositoryNotFoundException(repoId)

        val visibilityCode = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.id == repo.visibilityCodeId }
            ?.code ?: throw InvalidVisibilityCodeException(repo.visibilityCodeId?.toString())
        val visibility = visibilityCode.uppercase()

        if (visibility == "PRIVATE") {
            val isOwner = repo.owner.id == currentUserId
            val isCollaborator = currentUserId?.let {
                collaboratorRepository.existsByRepositoryIdAndUserId(repoId, it)
            } ?: false
            if (!isOwner && !isCollaborator) {
                throw RepositoryAccessDeniedException(repoId, currentUserId)
            }
        }

        val targetBranchShort = branch ?: repo.defaultBranch
        val targetBranchFull = GitRefUtils.toFullRef(targetBranchShort)

        if (branchRepository.findByRepositoryIdAndName(repoId, targetBranchFull) == null) {
            throw BranchNotFoundException(repoId, targetBranchFull)
        }

        val rawCommit = commitService.getLatestCommitHash(repoId, targetBranchFull)
            ?: throw HeadCommitNotFoundException(targetBranchShort)
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
            val readmeInfoFork = scope.fork { readmeService.getReadmeInfo(repoId, mainCommit.hash) }
            val readmeContentFork = scope.fork { readmeService.getReadmeContent(repoId, mainCommit.hash) }
            val readmeHtmlFork = scope.fork { readmeService.getReadmeHtml(repoId, mainCommit.hash) }
            val languageStatsFork = scope.fork { readmeService.getLanguageStats(repoId) }
            val cloneUrlsFork = scope.fork { gitService.getCloneUrls(repo) }

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

        val branches = branchRepository.findAllByRepositoryId(repoId).map { entity ->
            val short = GitRefUtils.toShortName(entity.name)!!
            val commit = commitService.getLatestCommitHash(repoId, entity.name)
                ?: throw HeadCommitNotFoundException(short)
            val enriched = commit.copy(author = enrichAuthor(commit.author))

            BranchResponse(
                name = short,
                qualifiedName = entity.name,
                isDefault = (short == repo.defaultBranch),
                isProtected = entity.isProtected,
                createdAt = entity.createdAt,
                headCommit = enriched,
                creator = entity.creator?.let { user ->
                    RepositoryUserResponse(
                        userId = user.id,
                        nickname = user.name ?: "이름 없음",
                        profileImageUrl = user.profileImageUrl
                    )
                } ?: RepositoryUserResponse(
                    userId = 0L,
                    nickname = "알 수 없음",
                    profileImageUrl = null
                )
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

        val fileTree = commitService.getFileTreeAtRoot(repoId, mainCommit.hash, targetBranchShort)
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

    /**
     * 현재 사용자의 저장소 목록 (커서 기반)
     */
    @Transactional(readOnly = true)
    fun getMyRepositoriesConnection(
        currentUserId: Long,
        paging: KeysetPaging,
        sortBy: String,
        sortDirection: String,
        keyword: String?
    ): RepositoryListItemConnection {
        // 1)  레포 ID 수집
        val idFilter = collectMyRepositoryIdsForConnection(currentUserId)
        if (idFilter.isEmpty()) {
            return RepositoryListItemConnection(
                edges = emptyList(),
                pageInfo = PageInfoDTO(false, false, null, null),
                totalCount = 0
            )
        }

        // 2) 정렬 파싱
        val sort = toRepoSort(sortBy)
        val dir = toDir(sortDirection)

        // 3) 조회
        val rows = repositoryKeysetJdbcRepository.query(
            req = RepoKeysetReq(
                paging = paging,
                sort = sort,
                dir = dir,
                keyword = keyword,
                idFilter = idFilter
            ),
            limit = paging.pageSize + 1
        )

        // 4) 슬라이싱
        val (slice, hasNext, hasPrev) =
            if (paging.isForward) {
                Triple(rows.take(paging.pageSize), rows.size > paging.pageSize, paging.after != null)
            } else {
                Triple(rows.take(paging.pageSize).asReversed(), paging.before != null, rows.size > paging.pageSize)
            }

        // 집합
        val ownSet = repositoryRepository.findByOwnerIdAndIsDeletedFalse(currentUserId, Pageable.unpaged())
            .map { it.id }.toSet()
        val invitedSet = collaboratorRepository.findAcceptedByUserId(currentUserId)
            .map { it.repository.id }.toSet()
        val bookmarkedSet = bookmarkRepository.findByUserId(currentUserId)
            .map { it.repository.id }.toSet()
        val visibilityCodes = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")

        // 5) 매핑 커서
        val edges = slice.map { row ->
            val visibility = visibilityCodes.firstOrNull { it.id == row.visibilityCodeId }?.code ?: "unknown"
            val item = mapRowToListItem(
                row = row,
                visibilityCode = visibility,
                isOwner = ownSet.contains(row.id),
                isBookmarked = bookmarkedSet.contains(row.id),
                isInvited = invitedSet.contains(row.id)
            )
            val cursor = try {
                buildRepoCursor(row, sort, dir)
            } catch (e: Exception) {
                throw CursorEncodeFailedException("레포지토리 커서 인코딩 실패(id=${row.id})", e)
            }
            RepositoryListItemEdge(cursor = cursor, node = item)
        }

        val pageInfo = PageInfoDTO(
            hasNextPage = hasNext,
            hasPreviousPage = hasPrev,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor
        )

        return RepositoryListItemConnection(edges = edges, pageInfo = pageInfo, totalCount = null)
    }

    /**
     * 특정 사용자의 저장소 목록 (커서 기반)
     */
    @Transactional(readOnly = true)
    fun getUserRepositoriesConnection(
        targetUserId: Long,
        currentUserId: Long?,
        paging: KeysetPaging,
        sortBy: String,
        sortDirection: String,
        keyword: String?
    ): RepositoryListItemConnection {

        //1) 대상 사용자의 레포 ID 수집
        val idFilter = collectUserRepositoryIdsForConnection(targetUserId, currentUserId)
        if (idFilter.isEmpty()) {
            return RepositoryListItemConnection(
                edges = emptyList(),
                pageInfo = PageInfoDTO(false, false, null, null),
                totalCount = 0
            )
        }

        // 2) 정렬 파싱
        val sort = toRepoSort(sortBy)
        val dir = toDir(sortDirection)

        // 3) 조회
        val rows = repositoryKeysetJdbcRepository.query(
            req = RepoKeysetReq(
                paging = paging,
                sort = sort,
                dir = dir,
                keyword = keyword,
                idFilter = idFilter
            ),
            limit = paging.pageSize + 1
        )

        // 4) 슬라이싱
        val (slice, hasNext, hasPrev) =
            if (paging.isForward) {
                Triple(rows.take(paging.pageSize), rows.size > paging.pageSize, paging.after != null)
            } else {
                Triple(rows.take(paging.pageSize).asReversed(), paging.before != null, rows.size > paging.pageSize)
            }

        val ownSet = repositoryRepository.findByOwnerIdAndIsDeletedFalse(targetUserId, Pageable.unpaged())
            .map { it.id }.toSet()
        val invitedSet = currentUserId?.let {
            collaboratorRepository.findAcceptedByUserIdAndOwnerId(it, targetUserId).map { c -> c.repository.id }.toSet()
        } ?: emptySet()
        val bookmarkedSet = currentUserId?.let {
            bookmarkRepository.findByUserId(it).map { b -> b.repository.id }.toSet()
        } ?: emptySet()
        val visibilityCodes = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")

        val edges = slice.map { row ->
            val visibility = visibilityCodes.firstOrNull { it.id == row.visibilityCodeId }?.code ?: "unknown"
            val item = mapRowToListItem(
                row = row,
                visibilityCode = visibility,
                isOwner = ownSet.contains(row.id),
                isBookmarked = bookmarkedSet.contains(row.id),
                isInvited = invitedSet.contains(row.id)
            )
            val cursor = try {
                buildRepoCursor(row, sort, dir)
            } catch (e: Exception) {
                throw CursorEncodeFailedException("레포지토리 커서 인코딩 실패(id=${row.id})", e)
            }
            RepositoryListItemEdge(cursor = cursor, node = item)
        }

        val pageInfo = PageInfoDTO(
            hasNextPage = hasNext,
            hasPreviousPage = hasPrev,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor
        )

        return RepositoryListItemConnection(edges = edges, pageInfo = pageInfo, totalCount = null)
    }

    /**
     * 정렬 기준을 문자열로부터 RepoSortBy로 변환
     */
    private fun toRepoSort(sortBy: String?): RepoSortBy =
        when (sortBy?.lowercase()) {
            "name" -> RepoSortBy.NAME
            "updatedat", "lastupdatedat", "updated_at", "last_updated_at" -> RepoSortBy.UPDATED_AT
            else -> RepoSortBy.UPDATED_AT
        }

    /**
     * 정렬 방향을 문자열로부터 KeysetDir로 변환
     */
    private fun toDir(dir: String?): KeysetDir =
        if (dir.equals("ASC", true)) KeysetDir.ASC else KeysetDir.DESC

    /**
     * RepoRow로부터 커서 문자열 생성
     * - sort: 정렬 기준
     * - dir: 정렬 방향
     */
    private fun buildRepoCursor(row: RepoRow, sort: RepoSortBy, dir: KeysetDir): String {
        val payload = when (sort) {
            RepoSortBy.UPDATED_AT -> {
                val ts = (row.updatedAt ?: row.createdAt)
                    .atOffset(ZoneOffset.UTC).toInstant().toString()
                CursorPayload(
                    sort = "UPDATED_AT",
                    dir = dir.name,
                    k = mapOf("updatedAt" to ts, "id" to row.id.toString())
                )
            }
            RepoSortBy.NAME -> {
                CursorPayload(
                    sort = "NAME",
                    dir = dir.name,
                    k = mapOf("name" to row.name, "id" to row.id.toString())
                )
            }
        }
        return CursorCodec.encode(payload)
    }

    /**
     * RepoRow를 RepositoryListItem로 매핑
     */
    private fun mapRowToListItem(
        row: RepoRow,
        visibilityCode: String,
        isOwner: Boolean,
        isBookmarked: Boolean,
        isInvited: Boolean
    ): RepositoryListItem =
        RepositoryListItem(
            id = row.id,
            name = row.name,
            description = row.description,
            visibility = visibilityCode,
            lastUpdatedAt = (row.updatedAt ?: row.createdAt).toString(),
            isOwner = isOwner,
            isBookmarked = isBookmarked,
            isInvited = isInvited,
            language = row.language,
            ownerInfo = if (!isOwner) RepositoryUserResponse(
                userId = row.ownerId,
                nickname = row.ownerName ?: "이름 없음",
                profileImageUrl = row.ownerProfileImageUrl
            ) else null
        )

    /**
     * 현재 사용자의 저장소 ID 수집
     * - PUBLIC: 모든 사용자에게 공개
     * - PRIVATE: 현재 사용자와 협업자로 초대된 경우에만 접근 가능
     * - BOOKMARKED: 북마크한 저장소는 공개 여부와 관계없이 포함(단, 최소 초대 수락 또는 공개)
     */
    private fun collectMyRepositoryIdsForConnection(currentUserId: Long): List<Long> {
        val ownIds = repositoryRepository
            .findByOwnerIdAndIsDeletedFalse(currentUserId, Pageable.unpaged())
            .map { it.id }

        val invitedIds = collaboratorRepository.findAcceptedByUserId(currentUserId)
            .map { it.repository.id }

        val bookmarkedIds = bookmarkRepository.findByUserId(currentUserId)
            .map { it.repository.id }

        val invitedSet = invitedIds.toSet()
        val filteredBookmarkedIds = repositoryRepository.findAllById(bookmarkedIds)
            .filter { repo ->
                val isPublic = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
                    .firstOrNull { it.id == repo.visibilityCodeId }?.code?.equals("PUBLIC", true) == true
                isPublic || invitedSet.contains(repo.id)
            }
            .map { it.id }

        return (ownIds + invitedIds + filteredBookmarkedIds).distinct()
    }

    /**
     * 특정 사용자의 저장소 ID 수집
     * - PUBLIC: 모든 사용자에게 공개
     * - PRIVATE: 현재 사용자와 협업자로 초대된 경우에만 접근 가능
     */
    private fun collectUserRepositoryIdsForConnection(
        targetUserId: Long,
        currentUserId: Long?
    ): List<Long> {
        val visibilityCodes = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
        val publicCodeId = visibilityCodes.firstOrNull { it.code.equals("PUBLIC", true) }?.id
            ?: throw InvalidVisibilityCodeException("PUBLIC")
        val privateCodeId = visibilityCodes.firstOrNull { it.code.equals("PRIVATE", true) }?.id
            ?: throw InvalidVisibilityCodeException("PRIVATE")

        val publicIds = repositoryRepository
            .findByOwnerIdAndVisibilityCodeIdAndIsDeletedFalse(targetUserId, publicCodeId)
            .map { it.id }

        val invitedPrivateIds = if (currentUserId != null) {
            collaboratorRepository.findAcceptedByUserIdAndOwnerId(currentUserId, targetUserId)
                .map { it.repository }
                .filter { it.visibilityCodeId == privateCodeId && !it.isDeleted }
                .map { it.id }
        } else emptyList()

        return (publicIds + invitedPrivateIds).distinct()
    }
}
