package com.example.gitserver.module.pullrequest.infrastructure.persistence

import com.example.gitserver.module.pullrequest.application.query.model.CommitRow
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PullRequestCommitJdbcRepository(
    private val jdbc: NamedParameterJdbcTemplate
)  {

    fun countByPrId(prId: Long): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pull_request_commit WHERE pull_request_id=:prId",
            mapOf("prId" to prId),
            Int::class.java
        )!!

    fun sliceForward(prId: Long, afterSeq: Int?, limitPlusOne: Int): List<CommitRow> {
        val sql = buildString {
            append("SELECT commit_hash, seq FROM pull_request_commit WHERE pull_request_id=:prId ")
            if (afterSeq != null) append("AND seq > :afterSeq ")
            append("ORDER BY seq ASC LIMIT :limit")
        }
        val params = mutableMapOf<String, Any>("prId" to prId, "limit" to limitPlusOne)
        if (afterSeq != null) params["afterSeq"] = afterSeq
        return jdbc.query(sql, params) { rs, _ ->
            CommitRow(rs.getString("commit_hash"), rs.getInt("seq"))
        }
    }

    fun sliceBackward(prId: Long, beforeSeq: Int?, limitPlusOne: Int): List<CommitRow> {
        val sql = buildString {
            append("SELECT commit_hash, seq FROM pull_request_commit WHERE pull_request_id=:prId ")
            if (beforeSeq != null) append("AND seq < :beforeSeq ")
            append("ORDER BY seq DESC LIMIT :limit")
        }
        val params = mutableMapOf<String, Any>("prId" to prId, "limit" to limitPlusOne)
        if (beforeSeq != null) params["beforeSeq"] = beforeSeq
        return jdbc.query(sql, params) { rs, _ ->
            CommitRow(rs.getString("commit_hash"), rs.getInt("seq"))
        }
    }
}
