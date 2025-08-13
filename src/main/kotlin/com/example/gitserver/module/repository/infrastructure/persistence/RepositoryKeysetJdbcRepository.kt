package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.common.pagination.CursorCodec
import com.example.gitserver.common.pagination.CursorPayload
import com.example.gitserver.common.pagination.SortDirection
import com.example.gitserver.module.repository.application.query.model.RepoKeysetReq
import com.example.gitserver.module.repository.application.query.model.RepoRow
import com.example.gitserver.module.repository.application.query.model.RepoSortBy
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class RepositoryKeysetJdbcRepository(
    private val jdbc: NamedParameterJdbcTemplate,
): RepositoryKeySetRepository {

    override fun query(req: RepoKeysetReq, limit: Int): List<RepoRow> {
        val (sql, params) = buildSql(req, limit)
        return jdbc.query(sql, params, rowMapper)
    }

    private val rowMapper = RowMapper<RepoRow> { rs: ResultSet, _ ->
        RepoRow(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            updatedAt = rs.getObject("updated_at", LocalDateTime::class.java)?.takeIf { !rs.wasNull() },
            createdAt = rs.getObject("created_at", LocalDateTime::class.java),
            visibilityCodeId = rs.getObject("visibility_code_id")?.let { rs.getLong("visibility_code_id") },
            language = rs.getString("language"),
            ownerId = rs.getLong("owner_id"),
            ownerName = rs.getString("owner_name"),
            ownerProfileImageUrl = rs.getString("owner_profile_image_url")
        )
    }

    private fun buildSql(req: RepoKeysetReq, limit: Int): Pair<String, Map<String, Any?>> {
        val sb = StringBuilder()
        val params = mutableMapOf<String, Any?>(
            "ids" to (if (req.idFilter.isEmpty()) listOf(-1L) else req.idFilter),
            "limit" to (limit + 1)
        )

        sb.append(
            """
            SELECT r.id, r.name, r.description, r.updated_at, r.created_at, r.visibility_code_id, r.language,
                   u.id AS owner_id, u.name AS owner_name, u.profile_image_url AS owner_profile_image_url
            FROM repository r
            JOIN user u ON u.id = r.owner_id
            WHERE r.is_deleted = 0
              AND r.id IN (:ids)
            """.trimIndent()
        )

        if (!req.keyword.isNullOrBlank()) {
            sb.append(" AND (LOWER(r.name) LIKE :kw OR LOWER(COALESCE(r.description,'')) LIKE :kw)")
            params["kw"] = "%${req.keyword.lowercase()}%"
        }

        applyCursorPredicate(req, sb, params)
        applyOrderBy(req, sb)

        sb.append(" LIMIT :limit")

        return sb.toString() to params
    }

    private fun applyCursorPredicate(req: RepoKeysetReq, sb: StringBuilder, params: MutableMap<String, Any?>) {
        val cursorStr = if (req.paging.isForward) req.paging.after else req.paging.before
        if (cursorStr.isNullOrBlank()) return

        val c: CursorPayload = CursorCodec.decode(cursorStr)
        when (req.sort) {
            RepoSortBy.UPDATED_AT -> {
                val lastIso = c.k["updatedAt"] ?: "1970-01-01T00:00:00Z"
                val last = LocalDateTime.ofInstant(Instant.parse(lastIso), ZoneOffset.UTC)
                params["c_updated"] = last
                params["c_id"] = c.k["id"]!!.toLong()

                val isForward = req.paging.isForward
                val (cmp, tie) = when (req.dir) {
                    SortDirection.DESC -> if (isForward) "<" to "<" else ">" to ">"
                    SortDirection.ASC  -> if (isForward) ">" to ">" else "<" to "<"
                }
                sb.append(
                    """
                    AND (
                      COALESCE(r.updated_at, r.created_at) $cmp :c_updated
                      OR (COALESCE(r.updated_at, r.created_at) = :c_updated AND r.id $tie :c_id)
                    )
                    """.trimIndent()
                )
            }
            RepoSortBy.NAME -> {
                val name = c.k["name"]!!
                params["c_name"] = name
                params["c_id"] = c.k["id"]!!.toLong()

                val isForward = req.paging.isForward
                val (cmp, tie) = when (req.dir) {
                    SortDirection.DESC -> if (isForward) "<" to "<" else ">" to ">"
                    SortDirection.ASC  -> if (isForward) ">" to ">" else "<" to "<"
                }
                sb.append(
                    """
                    AND (
                      r.name $cmp :c_name
                      OR (r.name = :c_name AND r.id $tie :c_id)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun applyOrderBy(req: RepoKeysetReq, sb: StringBuilder) {
        val dir = if (req.dir == SortDirection.DESC) "DESC" else "ASC"
        when (req.sort) {
            RepoSortBy.UPDATED_AT ->
                sb.append(" ORDER BY COALESCE(r.updated_at, r.created_at) $dir, r.id $dir")
            RepoSortBy.NAME ->
                sb.append(" ORDER BY r.name $dir, r.id $dir")
        }
    }
}

