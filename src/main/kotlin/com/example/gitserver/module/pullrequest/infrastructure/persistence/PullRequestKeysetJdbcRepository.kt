package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.common.pagination.CursorCodec
import com.example.gitserver.common.pagination.CursorPayload
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.common.pagination.SortDirection
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestKeysetReq
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItem
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestSortBy
import com.example.gitserver.module.pullrequest.application.query.model.RepositoryUser
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class PullRequestKeysetJdbcRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {
    private val listMapper = RowMapper<PullRequestListItem> { rs: ResultSet, _ ->
        PullRequestListItem(
            id = rs.getLong("id"),
            repositoryId = rs.getLong("repository_id"),
            title = rs.getString("title"),
            status = rs.getString("status_code"),
            sourceBranch = rs.getString("source_branch"),
            targetBranch = rs.getString("target_branch"),
            author = RepositoryUser(
                userId = rs.getLong("author_id"),
                nickname = rs.getString("author_name"),
                profileImageUrl = rs.getString("author_profile_image_url")
            ),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
            updatedAt = rs.getTimestamp("updated_at")?.toLocalDateTime()
        )
    }

    fun query(req: PullRequestKeysetReq): List<PullRequestListItem> {
        val pageSize = req.paging.pageSize
        val (sql, params) = buildSqlQuery(req, pageSize + 1)
        return jdbc.query(sql, params, listMapper)
    }

    private fun buildSqlQuery(req: PullRequestKeysetReq, limit: Int): Pair<String, Map<String, Any?>> {
        val sb = StringBuilder()
        val params = mutableMapOf<String, Any?>(
            "repoId" to req.repoId,
            "limit" to limit
        )

        sb.append(
            """
            SELECT pr.id, pr.repository_id, pr.title, pr.description,
                   pr.status_code_id, c.code AS status_code,
                   pr.source_branch, pr.target_branch,
                   pr.base_commit_hash, pr.head_commit_hash,
                   pr.author_id, u.name AS author_name, u.profile_image_url AS author_profile_image_url,
                   pr.created_at, pr.updated_at
            FROM pull_request pr
            JOIN user u ON u.id = pr.author_id
            LEFT JOIN common_code_detail c ON c.id = pr.status_code_id
            WHERE pr.repository_id = :repoId
            """.trimIndent()
        )

        if (!req.keyword.isNullOrBlank()) {
            sb.append(" AND (LOWER(pr.title) LIKE :kw OR LOWER(COALESCE(pr.description,'')) LIKE :kw)")
            params["kw"] = "%${req.keyword.lowercase()}%"
        }
        if (!req.status.isNullOrBlank()) {
            sb.append(" AND c.code = :status")
            params["status"] = req.status.lowercase()
        }

        applyCursorPredicate(req, sb, params)
        applyOrderBy(req, sb)
        sb.append(" LIMIT :limit")

        return sb.toString() to params
    }

    private fun applyCursorPredicate(
        req: PullRequestKeysetReq,
        sb: StringBuilder,
        params: MutableMap<String, Any?>
    ) {
        val cursorStr = if (req.paging.isForward) req.paging.after else req.paging.before
        if (cursorStr.isNullOrBlank()) return

        val cursor: CursorPayload = CursorCodec.decode(cursorStr)
        PagingValidator.ensureCursorMatchesSort(cursor, req.sort.name, req.dir.name)

        val isForward = req.paging.isForward
        val cmpPrimary: String
        val cmpTie: String
        if (req.dir == SortDirection.DESC) {
            cmpPrimary = if (isForward) "<" else ">"
            cmpTie = cmpPrimary
        } else {
            cmpPrimary = if (isForward) ">" else "<"
            cmpTie = cmpPrimary
        }

        when (req.sort) {
            PullRequestSortBy.UPDATED_AT -> {
                val updatedIso = cursor.k["updatedAt"] ?: "1970-01-01T00:00:00Z"
                val updatedAt = LocalDateTime.ofInstant(Instant.parse(updatedIso), ZoneOffset.UTC)
                params["c_updated"] = updatedAt
                params["c_id"] = cursor.k["id"]!!.toLong()
                sb.append(
                    """
                    AND (
                        (COALESCE(pr.updated_at, pr.created_at) $cmpPrimary :c_updated) OR
                        (COALESCE(pr.updated_at, pr.created_at) = :c_updated AND pr.id $cmpTie :c_id)
                    )
                    """.trimIndent()
                )
            }

            PullRequestSortBy.CREATED_AT -> {
                val createdIso = cursor.k["createdAt"] ?: "1970-01-01T00:00:00Z"
                val createdAt = LocalDateTime.ofInstant(Instant.parse(createdIso), ZoneOffset.UTC)
                params["c_created"] = createdAt
                params["c_id"] = cursor.k["id"]!!.toLong()
                sb.append(
                    """
                    AND (
                        (pr.created_at $cmpPrimary :c_created) OR
                        (pr.created_at = :c_created AND pr.id $cmpTie :c_id)
                    )
                    """.trimIndent()
                )
            }

            PullRequestSortBy.TITLE -> {
                val title = cursor.k["title"] ?: ""
                params["c_title"] = title
                params["c_id"] = cursor.k["id"]!!.toLong()
                sb.append(
                    """
                    AND (
                        (LOWER(pr.title) $cmpPrimary :c_title) OR
                        (LOWER(pr.title) = :c_title AND pr.id $cmpTie :c_id)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun applyOrderBy(req: PullRequestKeysetReq, sb: StringBuilder) {
        val dir = if (req.dir == SortDirection.DESC) "DESC" else "ASC"
        when (req.sort) {
            PullRequestSortBy.UPDATED_AT ->
                sb.append(" ORDER BY COALESCE(pr.updated_at, pr.created_at) $dir, pr.id $dir")
            PullRequestSortBy.CREATED_AT ->
                sb.append(" ORDER BY pr.created_at $dir, pr.id $dir")
            PullRequestSortBy.TITLE ->
                sb.append(" ORDER BY LOWER(pr.title) $dir, pr.id $dir")
        }
    }
}
