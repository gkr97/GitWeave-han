package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.application.query.model.*
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class PullRequestJdbcRepository(
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

    private val detailMapper = RowMapper<PullRequestDetail> { rs: ResultSet, _ ->
        PullRequestDetail(
            id = rs.getLong("id"),
            repositoryId = rs.getLong("repository_id"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            status = rs.getString("status_code"),
            sourceBranch = rs.getString("source_branch"),
            targetBranch = rs.getString("target_branch"),
            baseCommitHash = rs.getString("base_commit_hash"),
            headCommitHash = rs.getString("head_commit_hash"),
            author = RepositoryUser(
                userId = rs.getLong("author_id"),
                nickname = rs.getString("author_name"),
                profileImageUrl = rs.getString("author_profile_image_url")
            ),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
            updatedAt = rs.getTimestamp("updated_at")?.toLocalDateTime()
        )
    }

    fun queryList(
        repositoryId: Long,
        keyword: String?,
        status: String?,
        sort: String,
        dir: String,
        page: Int,
        size: Int
    ): Pair<List<PullRequestListItem>, Int> {
        val sb = StringBuilder(
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
        val params = mutableMapOf<String, Any?>("repoId" to repositoryId)

        if (!keyword.isNullOrBlank()) {
            sb.append(" AND (LOWER(pr.title) LIKE :kw OR LOWER(COALESCE(pr.description,'')) LIKE :kw)")
            params["kw"] = "%${keyword.lowercase()}%"
        }
        if (!status.isNullOrBlank()) {
            sb.append(" AND c.code = :status")
            params["status"] = status.lowercase()
        }

        val orderBy = when (sort) {
            "title"      -> "pr.title"
            "createdAt"  -> "pr.created_at"
            else         -> "COALESCE(pr.updated_at, pr.created_at)"
        }
        val direction = if (dir.equals("ASC", true)) "ASC" else "DESC"
        sb.append(" ORDER BY $orderBy $direction, pr.id $direction")
        sb.append(" LIMIT :limit OFFSET :offset")
        params["limit"] = size
        params["offset"] = page * size

        val content = jdbc.query(sb.toString(), params, listMapper)

        val total = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM pull_request pr
            LEFT JOIN common_code_detail c ON c.id = pr.status_code_id
            WHERE pr.repository_id = :repoId
              ${if (keyword.isNullOrBlank()) "" else "AND (LOWER(pr.title) LIKE :kw OR LOWER(COALESCE(pr.description,'')) LIKE :kw)"}
              ${if (status.isNullOrBlank()) "" else "AND c.code = :status"}
            """.trimIndent(),
            params, Int::class.java
        ) ?: 0

        return content to total
    }

    fun queryDetail(repositoryId: Long, prId: Long): PullRequestDetail? =
        jdbc.query(
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
            WHERE pr.repository_id = :repoId AND pr.id = :prId
            """.trimIndent(),
            mapOf("repoId" to repositoryId, "prId" to prId),
            detailMapper
        ).firstOrNull()
}
