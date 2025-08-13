package com.example.gitserver.module.repository.application.query.keyset

import com.example.gitserver.common.pagination.CursorCodec
import com.example.gitserver.common.pagination.CursorPayload
import com.example.gitserver.common.pagination.SortDirection
import com.example.gitserver.module.repository.application.query.model.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchKeysetRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class BranchKeysetJdbcRepository(
    private val jdbc: NamedParameterJdbcTemplate
) : BranchKeysetRepository {

    override fun query(req: BranchKeysetReq): List<BranchRow> {
        val pageSize = req.paging.pageSize
        val (sql, params) = buildSqlQuery(req, pageSize + 1)
        return jdbc.query(sql, params, rowMapper)
    }

    private val rowMapper = RowMapper<BranchRow> { rs: ResultSet, _ ->
        BranchRow(
            id = rs.getLong("id"),
            repositoryId = rs.getLong("repository_id"),
            name = rs.getString("name"),
            isDefault = rs.getBoolean("is_default"),
            isProtected = rs.getBoolean("is_protected"),
            createdAt = rs.getObject("created_at", LocalDateTime::class.java),
            headCommitHash = rs.getString("head_commit_hash")?.takeIf { !rs.wasNull() },
            lastCommitAt = rs.getObject("last_commit_at", LocalDateTime::class.java)?.takeIf { !rs.wasNull() },
            creatorId = rs.getLong("creator_id").let { if (rs.wasNull()) null else it },
            creatorNickname = rs.getString("creator_nickname")?.takeIf { !rs.wasNull() },
            creatorProfileImageUrl = rs.getString("creator_profile_image_url")?.takeIf { !rs.wasNull() }
        )
    }

    private fun buildSqlQuery(req: BranchKeysetReq, limit: Int): Pair<String, Map<String, Any?>> {
        val sb = StringBuilder()
        val params = mutableMapOf<String, Any?>(
            "repoId" to req.repoId,
            "limit" to limit
        )

        sb.append(
            """
            SELECT 
              b.id,
              b.repository_id,
              b.name,
              b.is_default,
              b.is_protected,
              b.created_at,
              b.head_commit_hash,
              b.last_commit_at,
              b.creator_id,
              u.name AS creator_nickname,
              u.profile_image_url AS creator_profile_image_url
            FROM branch b
            LEFT JOIN user u ON u.id = b.creator_id
            WHERE b.repository_id = :repoId
            """.trimIndent()
        )

        if (req.onlyMine && req.currentUserId != null) {
            sb.append(" AND b.creator_id = :currentUserId")
            params["currentUserId"] = req.currentUserId
        }

        if (!req.keyword.isNullOrBlank()) {
            sb.append(" AND LOWER(b.name) LIKE :kw")
            params["kw"] = "%${req.keyword.lowercase()}%"
        }

        applyCursorPredicate(req, sb, params)
        applyOrderBy(req, sb)
        sb.append(" LIMIT :limit")

        return sb.toString() to params
    }

    private fun applyCursorPredicate(
        req: BranchKeysetReq,
        sb: StringBuilder,
        params: MutableMap<String, Any?>
    ) {
        val cursorStr = if (req.paging.isForward) req.paging.after else req.paging.before
        if (cursorStr.isNullOrBlank()) return

        val c: CursorPayload = CursorCodec.decode(cursorStr)
        when (req.sort) {
            BranchSortBy.LAST_COMMIT_AT -> {
                val lastCommitIso = c.k["lastCommitAt"] ?: "1970-01-01T00:00:00Z"
                val lastCommitAt = LocalDateTime.ofInstant(Instant.parse(lastCommitIso), ZoneOffset.UTC)
                params["c_last"] = lastCommitAt
                params["c_id"] = c.k["id"]!!.toLong()

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
                sb.append(
                    """
                    AND (
                        (COALESCE(b.last_commit_at, TIMESTAMP '1970-01-01 00:00:00') $cmpPrimary :c_last) OR
                        (COALESCE(b.last_commit_at, TIMESTAMP '1970-01-01 00:00:00') = :c_last AND b.id $cmpTie :c_id)
                    )
                    """.trimIndent()
                )
            }

            BranchSortBy.NAME -> {
                val name = c.k["name"]!!
                params["c_name"] = name
                params["c_id"] = c.k["id"]!!.toLong()

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

                sb.append(
                    """
                    AND (
                        (b.name $cmpPrimary :c_name) OR
                        (b.name = :c_name AND b.id $cmpTie :c_id)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun applyOrderBy(req: BranchKeysetReq, sb: StringBuilder) {
        val dir = if (req.dir == SortDirection.DESC) "DESC" else "ASC"
        when (req.sort) {
            BranchSortBy.LAST_COMMIT_AT ->
                sb.append(" ORDER BY COALESCE(b.last_commit_at, TIMESTAMP '1970-01-01 00:00:00') $dir, b.id $dir")
            BranchSortBy.NAME ->
                sb.append(" ORDER BY b.name $dir, b.id $dir")
        }
    }
}
