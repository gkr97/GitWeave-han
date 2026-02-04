package com.example.gitserver.module.pullrequest.application.service

import com.example.gitserver.common.pagination.*
import com.example.gitserver.module.gitindex.indexer.application.query.CommitQueryService
import com.example.gitserver.module.pullrequest.application.query.model.CommitRow
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommitJdbcRepository
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PullRequestCommitConnectionService(
    private val prRepo: PullRequestRepository,
    private val commitJdbc: PullRequestCommitJdbcRepository,
    private val commitQuery: CommitQueryService
) {
    private val sortKey = "seq"
    private val dir = SortDirection.ASC.name

    private fun encodeCursor(seq: Int) =
        CursorCodec.encode(CursorPayload(sort = sortKey, dir = dir, k = mapOf("seq" to seq.toString())))

    private fun decodeSeq(cur: String?): Int? =
        cur?.let { CursorCodec.decode(it).also { p -> PagingValidator.ensureCursorMatchesSort(p, sortKey, dir) } }
            ?.k?.get("seq")?.toIntOrNull()


    /**
     * PR 커밋 연결 조회
     *
     * @param prId PR ID
     * @param repoId 저장소 ID (커밋 메타정보 조회용)
     * @param paging 페이지네이션 정보
     * @return 커밋 연결
     */
    @Transactional(readOnly = true)
    fun connection(prId: Long, repoId: Long, paging: KeysetPaging): Connection<CommitResponse> {
        PagingValidator.validate(paging)
        val pageSize = paging.pageSize.coerceAtMost(200)

        return when {
            paging.isForward -> {
                val afterSeq = decodeSeq(paging.after)
                val rows = commitJdbc.sliceForward(prId, afterSeq, pageSize + 1)
                val nodes = fetchCommitNodes(repoId, rows)
                val connRows = nodes.map { it.toNode() }
                val built = ConnectionBuilder.forward(connRows, pageSize) { encodeCursor(it.seq) }
                built.copy(
                    totalCount = commitJdbc.countByPrId(prId)
                ).mapNodes { it.node }
            }
            else -> { // backward
                val beforeSeq = decodeSeq(paging.before)
                val rowsDesc = commitJdbc.sliceBackward(prId, beforeSeq, pageSize + 1)
                val nodesDesc = fetchCommitNodes(repoId, rowsDesc)
                val connRows = nodesDesc.map { it.toNode() }
                val built = ConnectionBuilder.backward(connRows, pageSize) { encodeCursor(it.seq) }
                built.copy(
                    totalCount = commitJdbc.countByPrId(prId)
                ).mapNodes { it.node }
            }
        }
    }

    private data class CommitNode(val node: CommitResponse, val seq: Int)
    private fun CommitNode.toNode() = this

    private fun fetchCommitNodes(repoId: Long, rows: List<CommitRow>): List<CommitNode> {
        val hashes = rows.map { it.commitHash }
        val map = commitQuery.getCommitInfoBatch(repoId, hashes)
        return rows.map { r ->
            val meta = map[r.commitHash]
                ?: CommitResponse(
                    hash = r.commitHash,
                    message = "(metadata not indexed)",
                    committedAt = LocalDateTime.now(),
                    author = RepositoryUserResponse(-1, "unknown", null)
                )
            CommitNode(meta, r.seq)
        }
    }

    private fun <T, R> Connection<T>.mapNodes(mapper: (T) -> R): Connection<R> =
        Connection(
            edges = this.edges.map { Edge(it.cursor, mapper(it.node)) },
            pageInfo = this.pageInfo,
            totalCount = this.totalCount
        )

    fun findRepoId(prId: Long): Long =
        prRepo.findById(prId).orElseThrow().repository.id
}
