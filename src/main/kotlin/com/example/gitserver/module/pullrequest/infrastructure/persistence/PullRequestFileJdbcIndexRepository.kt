package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileRow
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileItem
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository

@Repository
class PullRequestFileJdbcIndexRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {

    private val insertSql = """
        INSERT INTO pull_request_file
            (pull_request_id, path, old_path, status_code_id, is_binary, additions, deletions, blob_hash, old_blob_hash)
        VALUES (:prId, :path, :oldPath, :statusCodeId, :isBinary, :additions, :deletions, :headBlobHash, :baseBlobHash)
    """.trimIndent()

    private val deleteSql = """
        DELETE FROM pull_request_file
        WHERE pull_request_id = :prId
    """.trimIndent()

    private val selectSql = """
        SELECT path, old_path, status_code_id, is_binary, additions, deletions, blob_hash, old_blob_hash
        FROM pull_request_file
        WHERE pull_request_id = :prId
        ORDER BY path ASC
    """.trimIndent()

    private val selectItemsSql = """
        SELECT 
            f.path,
            f.old_path,
            d.code AS status,
            f.is_binary,
            f.additions,
            f.deletions,
            f.blob_hash,
            f.old_blob_hash
        FROM pull_request_file f
        JOIN common_code_detail d
          ON d.id = f.status_code_id
        WHERE f.pull_request_id = :prId
        ORDER BY f.path ASC
    """.trimIndent()

    /**
     * 기존 행 전체 삭제 후 새 행 일괄 삽입.
     * 트랜잭션 경계는 서비스 레이어에서 잡아야 함.
     */
    fun replaceAll(prId: Long, rows: List<PullRequestFileRow>) {
        jdbc.update(deleteSql, mapOf("prId" to prId))

        if (rows.isEmpty()) return

        val params = rows.map { r ->
            MapSqlParameterSource()
                .addValue("prId", prId)
                .addValue("path", r.path)
                .addValue("oldPath", r.oldPath)
                .addValue("statusCodeId", r.statusCodeId)
                .addValue("isBinary", r.isBinary)
                .addValue("additions", r.additions)
                .addValue("deletions", r.deletions)
                .addValue("headBlobHash", r.headBlobHash)
                .addValue("baseBlobHash", r.baseBlobHash)
        }.toTypedArray()

        jdbc.batchUpdate(insertSql, params)
    }

    /**
     * 인덱스 원자료(코드 ID 기반) 조회
     */
    fun listByPrId(prId: Long): List<PullRequestFileRow> =
        jdbc.query(selectSql, mapOf("prId" to prId)) { rs, _ ->
            PullRequestFileRow(
                path = rs.getString("path"),
                oldPath = rs.getString("old_path"),
                statusCodeId = rs.getLong("status_code_id"),
                isBinary = rs.getBoolean("is_binary"),
                additions = rs.getInt("additions"),
                deletions = rs.getInt("deletions"),
                headBlobHash = rs.getString("blob_hash"),
                baseBlobHash = rs.getString("old_blob_hash")
            )
        }

    /**
     * 화면용 항목(상태 코드 문자열 포함) 조회
     * - 서비스에서 syntheticId만 채워 쓰면 됨
     */
    fun listItemsByPrId(prId: Long): List<PullRequestFileItem> =
        jdbc.query(selectItemsSql, mapOf("prId" to prId)) { rs, _ ->
            PullRequestFileItem(
                id = 0L,
                path = rs.getString("path"),
                oldPath = rs.getString("old_path"),
                status = rs.getString("status"),
                additions = rs.getInt("additions"),
                deletions = rs.getInt("deletions"),
                isBinary = rs.getBoolean("is_binary"),
                headBlobHash = rs.getString("blob_hash"),
                baseBlobHash = rs.getString("old_blob_hash")
            )
        }

}
