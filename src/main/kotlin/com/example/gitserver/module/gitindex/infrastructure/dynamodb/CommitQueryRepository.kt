package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class CommitQueryRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    fun getLatestCommit(repositoryId: Long, branch: String): CommitResponse? {
        log.info { "[CommitQueryRepository] 최신 커밋 조회 시작 - repositoryId=$repositoryId, branch=$branch" }

        val response = dynamoDbClient.query {
            it.tableName(tableName)
                .indexName("GSI_Commits_By_Branch_And_Date")
                .keyConditionExpression("branch = :branch")
                .expressionAttributeValues(mapOf(":branch" to AttributeValue.fromS(branch)))
                .scanIndexForward(false)
                .limit(10)
        }

        val item = response.items().firstOrNull {
            it["type"]?.s() == "commit" && it["PK"]?.s() == "REPO#$repositoryId"
        } ?: return null.also {
            log.warn { "[CommitQueryRepository] 커밋 없음 - repositoryId=$repositoryId, branch=$branch" }
        }

        val commitHash = item["SK"]?.s()?.split("#")?.lastOrNull()
        if (commitHash.isNullOrBlank()) {
            log.error { "[CommitQueryRepository] 커밋 해시 파싱 실패 - SK=${item["SK"]?.s()}" }
            return null
        }

        val committedAt = item["committed_at"]?.s()
            ?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull() }
            ?: LocalDateTime.now()

        return CommitResponse(
            hash = commitHash,
            message = item["message"]?.s() ?: "(no message)",
            committedAt = committedAt,
            author = RepositoryUserResponse(
                userId = item["author_id"]?.n()?.toLongOrNull() ?: -1L,
                nickname = item["author_name"]?.s() ?: "unknown",
                profileImageUrl = null
            )
        )
    }
}
