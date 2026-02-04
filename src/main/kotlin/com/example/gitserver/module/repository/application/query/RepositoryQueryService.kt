package com.example.gitserver.module.repository.application.query

import com.example.gitserver.common.pagination.CursorCodec
import com.example.gitserver.common.pagination.CursorPayload
import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PageInfoDTO
import com.example.gitserver.common.pagination.SortDirection as KeysetDir
import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.common.cache.RequestCache
import com.example.gitserver.module.gitindex.indexer.application.query.CommitQueryService
import com.example.gitserver.module.gitindex.indexer.application.query.ReadmeQueryService
import com.example.gitserver.module.gitindex.shared.domain.port.GitRepositoryPort
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.application.query.model.*
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.*
import com.example.gitserver.module.repository.interfaces.dto.*
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.StructuredTaskScope

private val log = KotlinLogging.logger {}

@Service
class RepositoryQueryService(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val starRepository: RepositoryStarRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val branchRepository: BranchRepository,
    private val repositoryStatsRepository: RepositoryStatsRepository,
    private val commitService: CommitQueryService,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val readmeService: ReadmeQueryService,
    private val userRepository: UserRepository,
    private val repositoryKeysetJdbcRepository: RepositoryKeySetRepository,
    private val gitRepositoryPort: GitRepositoryPort,
    private val requestCache: RequestCache,
) {

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = ["repoDetail"],
        key = "T(java.util.Objects).hash(#repoId, #branch, #currentUserId)"
    )
    fun getRepository(repoId: Long, branch: String? = null, currentUserId: Long?): RepoDetailResponse {
        log.info { "[getRepository] repoId=$repoId, branch=${branch ?: "default"} 시작" }

        val repo = requestCache.getRepo(repoId)
            ?: repositoryRepository.findByIdWithOwner(repoId)?.also { requestCache.putRepo(it) }
            ?: throw RepositoryNotFoundException(repoId)

        val visibilityMap = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .associateBy({ it.id }, { it.code.uppercase() })
        val visibility = visibilityMap[repo.visibilityCodeId]
            ?: throw InvalidVisibilityCodeException(repo.visibilityCodeId?.toString())

        val isOwnerFlag = currentUserId != null && repo.owner.id == currentUserId
        val isInvitedFlag = currentUserId?.let { uid ->
            requestCache.getCollabExists(repoId, uid)
                ?: collaboratorRepository.existsByRepositoryIdAndUserId(repoId, uid)
                    .also { requestCache.putCollabExists(repoId, uid, it) }
        } ?: false

        if (visibility == "PRIVATE") {
            if (!isOwnerFlag && !isInvitedFlag) {
                throw RepositoryAccessDeniedException(repoId, currentUserId)
            }
        }

        val targetBranchShort = branch ?: repo.defaultBranch
        val targetBranchFull = GitRefUtils.toFullRef(targetBranchShort)

        val cachedBranchId = requestCache.getBranchId(repoId, targetBranchFull, "exists")
        val branchId = cachedBranchId ?: branchRepository
            .findByRepositoryIdAndName(repoId, targetBranchFull)
            ?.id
            ?.also { requestCache.putBranchId(repoId, targetBranchFull, it, "exists") }

        if (branchId == null) throw BranchNotFoundException(repoId, targetBranchFull)

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
            val cloneUrlsFork = scope.fork { gitRepositoryPort.getCloneUrls(repo) }

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
            val placeholderCommit = CommitResponse(
                hash = entity.headCommitHash ?: "",
                message = "",
                committedAt = Instant.EPOCH.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                author = RepositoryUserResponse(0L, "unknown", null)
            )

            BranchResponse(
                name = short,
                qualifiedName = entity.name,
                isDefault = (short == repo.defaultBranch),
                isProtected = entity.isProtected,
                createdAt = entity.createdAt,
                headCommit = placeholderCommit,
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
                ),
                _repositoryId = repo.id
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

        val isStarredFlag = isStarred(repoId, currentUserId)

        return RepoDetailResponse(
            id = repo.id,
            name = repo.name,
            description = repo.description,
            visibility = visibility,
            createdAt = repo.createdAt,
            lastUpdatedAt = repo.updatedAt,
            owner = owner,
            isOwner = isOwnerFlag,
            isStarred = isStarredFlag,
            isInvited = isInvitedFlag,
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

    fun isStarred(repoId: Long, userId: Long?): Boolean {
        return userId?.let { starRepository.existsByUserIdAndRepositoryId(it, repoId) } ?: false
    }

    fun isInvited(repoId: Long, userId: Long?): Boolean {
        return userId?.let { collaboratorRepository.existsByRepositoryIdAndUserId(repoId, it) } ?: false
    }

    private fun enrichAuthor(user: RepositoryUserResponse): RepositoryUserResponse {
        val cached = runCatching { requestCache.getUser(user.userId) }.getOrNull()
        val userEntity = cached ?: userRepository.findById(user.userId).orElse(null)
            ?.also { runCatching { requestCache.putUser(it) } }
        return userEntity?.let {
            RepositoryUserResponse(
                userId = it.id,
                nickname = it.name ?: "알 수 없음",
                profileImageUrl = it.profileImageUrl
            )
        } ?: user
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = ["myRepos"],
        key = "T(java.util.Objects).hash(" +
                "#currentUserId, #paging.pageSize, #paging.after, #paging.before, " +
                "#sortBy, #sortDirection, #keyword)"
    )
    fun getMyRepositoriesConnection(
        currentUserId: Long,
        paging: KeysetPaging,
        sortBy: String,
        sortDirection: String,
        keyword: String?
    ): RepositoryListItemConnection {
        val idFilter = collectMyRepositoryIdsForConnection(currentUserId)
        if (idFilter.isEmpty()) {
            return RepositoryListItemConnection(
                edges = emptyList(),
                pageInfo = PageInfoDTO(false, false, null, null),
                totalCount = 0
            )
        }

        val sort = toRepoSort(sortBy)
        val dir = toDir(sortDirection)

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

        val (slice, hasNext, hasPrev) =
            if (paging.isForward) {
                Triple(rows.take(paging.pageSize), rows.size > paging.pageSize, paging.after != null)
            } else {
                Triple(rows.take(paging.pageSize).asReversed(), paging.before != null, rows.size > paging.pageSize)
            }

        val ownSet = repositoryRepository.findByOwnerIdAndIsDeletedFalse(currentUserId, Pageable.unpaged())
            .map { it.id }.toSet()
        val invitedSet = collaboratorRepository.findAcceptedByUserId(currentUserId)
            .map { it.repository.id }.toSet()
        val starredSet = starRepository.findByUserId(currentUserId)
            .map { it.repository.id }.toSet()

        val visibilityMap = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .associateBy({ it.id }, { it.code })

        val edges = slice.map { row ->
            val visibility = visibilityMap[row.visibilityCodeId] ?: "unknown"
            val item = mapRowToListItem(
                row = row,
                visibilityCode = visibility,
                isOwner = ownSet.contains(row.id),
                isStarred = starredSet.contains(row.id),
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

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = ["userRepos"],
        key = "T(java.util.Objects).hash(" +
                "#targetUserId, #currentUserId, #paging.pageSize, #paging.after, #paging.before, " +
                "#sortBy, #sortDirection, #keyword)"
    )
    fun getUserRepositoriesConnection(
        targetUserId: Long,
        currentUserId: Long?,
        paging: KeysetPaging,
        sortBy: String,
        sortDirection: String,
        keyword: String?
    ): RepositoryListItemConnection {

        val idFilter = collectUserRepositoryIdsForConnection(targetUserId, currentUserId)
        if (idFilter.isEmpty()) {
            return RepositoryListItemConnection(
                edges = emptyList(),
                pageInfo = PageInfoDTO(false, false, null, null),
                totalCount = 0
            )
        }

        val sort = toRepoSort(sortBy)
        val dir = toDir(sortDirection)

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
        val starredSet = currentUserId?.let {
            starRepository.findByUserId(it).map { s -> s.repository.id }.toSet()
        } ?: emptySet()

        val visibilityMap = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .associateBy({ it.id }, { it.code })

        val edges = slice.map { row ->
            val visibility = visibilityMap[row.visibilityCodeId] ?: "unknown"
            val item = mapRowToListItem(
                row = row,
                visibilityCode = visibility,
                isOwner = ownSet.contains(row.id),
                isStarred = starredSet.contains(row.id),
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

    private fun toRepoSort(sortBy: String?): RepoSortBy =
        when (sortBy?.lowercase()) {
            "name" -> RepoSortBy.NAME
            "updatedat", "lastupdatedat", "updated_at", "last_updated_at" -> RepoSortBy.UPDATED_AT
            else -> RepoSortBy.UPDATED_AT
        }

    private fun toDir(dir: String?): KeysetDir =
        if (dir.equals("ASC", true)) KeysetDir.ASC else KeysetDir.DESC

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

    private fun mapRowToListItem(
        row: RepoRow,
        visibilityCode: String,
        isOwner: Boolean,
        isStarred: Boolean,
        isInvited: Boolean
    ): RepositoryListItem =
        RepositoryListItem(
            id = row.id,
            name = row.name,
            description = row.description,
            visibility = visibilityCode,
            lastUpdatedAt = (row.updatedAt ?: row.createdAt).toString(),
            isOwner = isOwner,
            isStarred = isStarred,
            isInvited = isInvited,
            language = row.language,
            ownerInfo = if (!isOwner) RepositoryUserResponse(
                userId = row.ownerId,
                nickname = row.ownerName ?: "이름 없음",
                profileImageUrl = row.ownerProfileImageUrl
            ) else null
        )

    private fun collectMyRepositoryIdsForConnection(currentUserId: Long): List<Long> {
        val ownIds = repositoryRepository
            .findByOwnerIdAndIsDeletedFalse(currentUserId, Pageable.unpaged())
            .map { it.id }

        val invitedIds = collaboratorRepository.findAcceptedByUserId(currentUserId)
            .map { it.repository.id }

        val starredIds = starRepository.findByUserId(currentUserId)
            .map { it.repository.id }

        val invitedSet = invitedIds.toSet()

        val filteredStarredIds = repositoryRepository.findAllById(starredIds)
            .filter { repo ->
                val isPublic = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
                    .firstOrNull { it.id == repo.visibilityCodeId }?.code?.equals("PUBLIC", true) == true
                isPublic || invitedSet.contains(repo.id)
            }
            .map { it.id }

        return (ownIds + invitedIds + filteredStarredIds).distinct()
    }

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
