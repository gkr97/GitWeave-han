package com.example.gitserver.module.repository.application.query

import com.example.gitserver.common.pagination.*
import com.example.gitserver.module.gitindex.application.service.CommitService
import com.example.gitserver.module.repository.application.query.model.*
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchKeysetRepository
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Service
class BranchQueryService(
    private val branchRepository: BranchRepository,
    private val repositoryRepository: RepositoryRepository,
    private val commitService: CommitService,
    private val branchKeysetRepository: BranchKeysetRepository,
    private val repositoryAccessService: RepositoryAccessService
) {
    private val log = KotlinLogging.logger {}

    /** 과도한 조회 방지용 상한 */
    private val MAX_PAGE_SIZE = 100

    private fun String.toShortBranchName(): String = this.removePrefix("refs/heads/")

    /**
     * 브랜치 목록 조회 (키셋 페이징/GraphQL 커넥션)
     * - 페이징/정렬/권한 동일 규칙
     * - 키셋 조회 실패 → KeysetQueryFailedException
     * - 커서 인코딩 실패 → CursorEncodeFailedException
     * - 커밋 미존재 → HeadCommitNotFoundException
     */
    @Transactional(readOnly = true)
    fun getBranchConnection(
        repositoryId: Long,
        paging: KeysetPaging,
        sortBy: String,
        sortDirection: String,
        keyword: String?,
        onlyMine: Boolean,
        currentUserId: Long?
    ): BranchResponseConnection {
        log.info {
            "[getBranchConnection] repoId=$repositoryId paging=$paging sortBy=$sortBy sortDir=$sortDirection " +
                    "keyword=${keyword ?: ""} onlyMine=$onlyMine userId=${currentUserId ?: "null"}"
        }

        // 1) 저장소/권한
        val repo: Repository = repositoryRepository.findByIdAndIsDeletedFalse(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)
        repositoryAccessService.checkReadAccessOrThrow(repo, currentUserId)

        // 2) 페이징 검증
        try {
            PagingValidator.validate(paging)
        } catch (e: Exception) {
            throw InvalidPagingParameterException("키셋 페이징 파라미터가 유효하지 않습니다: $paging")
        }
        if (onlyMine && currentUserId == null) {
            throw AuthenticationRequiredException("onlyMine=true 사용 시 로그인 사용자 정보가 필요합니다.")
        }

        // 3) 정렬 파싱
        val sort = when (sortBy.uppercase()) {
            "LAST_COMMIT_AT" -> BranchSortBy.LAST_COMMIT_AT
            "NAME" -> BranchSortBy.NAME
            else -> throw InvalidSortFieldException(sortBy)
        }
        val dir = if (sortDirection.equals("ASC", true)) SortDirection.ASC else SortDirection.DESC

        // 4) 조회
        val rows: List<BranchRow> = try {
            branchKeysetRepository.query(
                BranchKeysetReq(
                    repoId = repositoryId,
                    paging = paging,
                    sort = sort,
                    dir = dir,
                    keyword = keyword,
                    onlyMine = onlyMine,
                    currentUserId = currentUserId
                )
            )
        } catch (e: Exception) {
            log.error(e) { "[getBranchConnection] 키셋 조회 실패" }
            throw KeysetQueryFailedException()
        }

        // 5) 슬라이싱
        val pageSize = paging.pageSize
        val (slice, hasNextPage, hasPreviousPage) =
            if (paging.isForward) {
                val hasNext = rows.size > pageSize
                Triple(rows.take(pageSize), hasNext, paging.after != null)
            } else {
                val hasPrev = rows.size > pageSize
                Triple(rows.take(pageSize).asReversed(), paging.before != null, hasPrev)
            }

        // 6) 커서/노드 매핑
        val edges = slice.map { row ->
            val cursor = try {
                branchCursorFromRow(row, sort, dir)
            } catch (e: Exception) {
                log.error(e) { "[getBranchConnection] 커서 인코딩 실패: rowId=${row.id}" }
                throw CursorEncodeFailedException()
            }
            BranchEdge(
                cursor = cursor,
                node = toBranchResponse(repositoryId, row)
            )
        }

        val pageInfo = PageInfoDTO(
            hasNextPage = hasNextPage,
            hasPreviousPage = hasPreviousPage,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor
        )

        return BranchResponseConnection(edges = edges, pageInfo = pageInfo, totalCount = null)
    }

    /**
     * BranchRow → 커서 문자열 변환
     * - 정렬 기준에 맞는 키로 커서 페이로드 생성
     */
    private fun branchCursorFromRow(row: BranchRow, sort: BranchSortBy, dir: SortDirection): String {
        val payload = when (sort) {
            BranchSortBy.LAST_COMMIT_AT -> CursorPayload(
                sort = "LAST_COMMIT_AT",
                dir = dir.name,
                k = mapOf(
                    "lastCommitAt" to (row.lastCommitAt
                        ?.atOffset(ZoneOffset.UTC)
                        ?.toInstant()
                        ?.toString() ?: "1970-01-01T00:00:00Z"),
                    "id" to row.id.toString()
                )
            )
            BranchSortBy.NAME -> CursorPayload(
                sort = "NAME",
                dir = dir.name,
                k = mapOf("name" to row.name, "id" to row.id.toString())
            )
        }
        return CursorCodec.encode(payload)
    }

    /**
     * BranchRow → BranchResponse 변환
     * - 커밋 없으면 HeadCommitNotFoundException
     */
    private fun toBranchResponse(repositoryId: Long, row: BranchRow): BranchResponse {
        val qualifiedName = row.name
        val shortName = qualifiedName.toShortBranchName()

        val commitDto: CommitResponse = try {
            val byHash = row.headCommitHash?.let {
                log.debug { "[toBranchResponse] 커밋 조회(by hash): rowId=${row.id} hash=$it" }
                commitService.getCommitInfo(repositoryId, it)
            }
            val byBranchLatest = if (byHash == null) {
                log.debug { "[toBranchResponse] 커밋 조회(최신 by branch): rowId=${row.id} ref=$qualifiedName" }
                commitService.getLatestCommitHash(repositoryId, qualifiedName)
            } else null

            (byHash ?: byBranchLatest) ?: throw HeadCommitNotFoundException(qualifiedName)
        } catch (e: HeadCommitNotFoundException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[toBranchResponse] 커밋 조회 중 예외" }
            throw HeadCommitNotFoundException(qualifiedName)
        }

        val creatorDto = row.creatorId?.let { uid ->
            RepositoryUserResponse(uid, row.creatorNickname ?: "", row.creatorProfileImageUrl)
        }

        return BranchResponse(
            name = shortName,
            qualifiedName = qualifiedName,
            isDefault = row.isDefault,
            isProtected = row.isProtected,
            createdAt = row.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
            headCommit = commitDto,
            creator = creatorDto
        )
    }
}
