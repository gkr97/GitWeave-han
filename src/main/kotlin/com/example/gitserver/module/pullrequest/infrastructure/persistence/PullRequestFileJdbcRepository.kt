package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileItem
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileDiffItem
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class PullRequestFileJdbcRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {
    private val fileMapper = RowMapper<PullRequestFileItem> { rs: ResultSet, _ ->
        PullRequestFileItem(
            id = rs.getLong("id"),
            filePath = rs.getString("path"),
            oldPath = rs.getString("old_path"),
            status = rs.getString("status_code"),
            additions = rs.getInt("additions"),
            deletions = rs.getInt("deletions"),
            isBinary = rs.getBoolean("is_binary"),
            headBlobHash = rs.getString("blob_hash"),
            baseBlobHash = rs.getString("old_blob_hash")
        )
    }

    private val diffMapper = RowMapper<PullRequestFileDiffItem> { rs: ResultSet, _ ->
        PullRequestFileDiffItem(
            id = rs.getLong("id"),
            filePath = rs.getString("path"),
            oldPath = rs.getString("old_path"),
            status = rs.getString("status_code"),
            additions = rs.getInt("additions"),
            deletions = rs.getInt("deletions"),
            isBinary = rs.getBoolean("is_binary"),
            headBlobHash = rs.getString("blob_hash"),
            baseBlobHash = rs.getString("old_blob_hash"),
            patch = null
        )
    }

    fun findFiles(prId: Long): List<PullRequestFileItem> =
        jdbc.query(
            """
            SELECT f.id, f.path, f.old_path, c.code as status_code,
                   f.additions, f.deletions, f.is_binary,
                   f.blob_hash, f.old_blob_hash
            FROM pull_request_file f
            LEFT JOIN common_code_detail c ON c.id = f.status_code_id
            WHERE f.pull_request_id = :prId
            ORDER BY f.path
            """.trimIndent(),
            mapOf("prId" to prId),
            fileMapper
        )

    fun findDiffs(prId: Long): List<PullRequestFileDiffItem> =
        jdbc.query(
            """
            SELECT f.id, f.path, f.old_path, c.code as status_code,
                   f.additions, f.deletions, f.is_binary,
                   f.blob_hash, f.old_blob_hash
            FROM pull_request_file f
            LEFT JOIN common_code_detail c ON c.id = f.status_code_id
            WHERE f.pull_request_id = :prId
            ORDER BY f.path
            """.trimIndent(),
            mapOf("prId" to prId),
            diffMapper
        )
}
