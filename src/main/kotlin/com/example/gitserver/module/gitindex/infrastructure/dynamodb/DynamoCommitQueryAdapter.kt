package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.port.CommitQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class DynamoCommitQueryAdapter(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) : CommitQueryRepository {
    private val log = KotlinLogging.logger {}

    private fun parseCommittedAt(raw: String?): Instant =
        try {
            if (raw.isNullOrBlank()) Instant.now() else Instant.parse(raw)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC).toInstant()
            } catch (_: Exception) {
                Instant.now()
            }
        }

    /**
     * 레포지토리의 최신 커밋을 조회합니다.
     * @param repositoryId 레포지토리 ID
     * @param branch 브랜치 이름
     * @return CommitResponse 객체 또는 null (존재하지 않는 경우)
     */
    override fun getLatestCommit(repositoryId: Long, branch: String): CommitResponse? {
        log.info { "[CommitQueryRepository] 최신 커밋 조회 시작 - repositoryId=$repositoryId, branch=$branch" }

        val resp = dynamoDbClient.query {
            it.tableName(tableName)
                .indexName("GSI_Commits_By_Branch_And_Date")
                .keyConditionExpression("#branch = :branch")
                .expressionAttributeNames(mapOf("#branch" to "branch", "#type" to "type"))
                // sealed=true 만 노출
                .filterExpression("PK = :pk AND #type = :commit AND sealed = :sealedTrue")
                .expressionAttributeValues(
                    mapOf(
                        ":branch" to AttributeValue.fromS(branch),
                        ":pk" to AttributeValue.fromS("REPO#$repositoryId"),
                        ":commit" to AttributeValue.fromS("commit"),
                        ":sealedTrue" to AttributeValue.fromBool(true),
                    )
                )
                .scanIndexForward(false)
                .limit(1)
        }

        val item = resp.items().firstOrNull() ?: run {
            log.warn { "[CommitQueryRepository] 커밋 없음 - repositoryId=$repositoryId, branch=$branch" }
            return null
        }

        val sk = item["SK"]?.s()
        val commitHash = sk?.split("#")?.getOrNull(1)
        if (commitHash.isNullOrBlank()) {
            log.error { "[CommitQueryRepository] 커밋 해시 파싱 실패 - SK=$sk" }
            return null
        }

        val committedAt = parseCommittedAt(item["committed_at"]?.s())

        return CommitResponse(
            hash = commitHash,
            message = item["message"]?.s() ?: "(no message)",
            committedAt = committedAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
            author = RepositoryUserResponse(
                userId = item["author_id"]?.n()?.toLongOrNull() ?: -1L,
                nickname = item["author_name"]?.s() ?: "unknown",
                profileImageUrl = item["author_profile_image_url"]?.s()
            )
        )
    }

    /**
     * 특정 커밋 해시의 정보를 조회합니다.
     * @param repositoryId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @return CommitResponse 객체 또는 null (존재하지 않는 경우)
     */
    override fun getCommitByHash(repositoryId: Long, commitHash: String): CommitResponse? {
        val pk = "REPO#$repositoryId"
        val skPrefix = "COMMIT#$commitHash"

        return try {
            val response = dynamoDbClient.query {
                it.tableName(tableName)
                    .keyConditionExpression("#pk = :pk AND begins_with(#sk, :sk)")
                    .expressionAttributeNames(mapOf("#pk" to "PK", "#sk" to "SK", "#type" to "type"))
                    // sealed=true 만 노출
                    .filterExpression("#type = :commit AND sealed = :sealedTrue")
                    .expressionAttributeValues(
                        mapOf(
                            ":pk" to AttributeValue.fromS(pk),
                            ":sk" to AttributeValue.fromS(skPrefix),
                            ":commit" to AttributeValue.fromS("commit"),
                            ":sealedTrue" to AttributeValue.fromBool(true)
                        )
                    )
                    .limit(1)
            }

            val item = response.items().firstOrNull() ?: return null

            CommitResponse(
                hash = commitHash,
                message = item["message"]?.s() ?: "(no message)",
                committedAt = parseCommittedAt(item["committed_at"]?.s())
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDateTime(),
                author = RepositoryUserResponse(
                    userId = item["author_id"]?.n()?.toLongOrNull() ?: -1L,
                    nickname = item["author_name"]?.s() ?: "unknown",
                    profileImageUrl = item["author_profile_image_url"]?.s()
                )
            )
        } catch (e: Exception) {
            log.error(e) { "[getCommitByHash] DynamoDB 커밋 조회 실패 repoId=$repositoryId, commitHash=$commitHash" }
            null
        }
    }

    /**
     * 특정 커밋 해시가 존재하는지 확인합니다.
     * @param repositoryId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @return 존재 여부
     */
    override fun existsCommit(repositoryId: Long, commitHash: String): Boolean {
        val pk = "REPO#$repositoryId"
        val skPrefix = "COMMIT#$commitHash#"

        return try {
            val resp = dynamoDbClient.query {
                it.tableName(tableName)
                    .keyConditionExpression("#pk = :pk AND begins_with(#sk, :sk)")
                    .expressionAttributeNames(mapOf("#pk" to "PK", "#sk" to "SK"))
                    .expressionAttributeValues(
                        mapOf(
                            ":pk" to AttributeValue.fromS(pk),
                            ":sk" to AttributeValue.fromS(skPrefix)
                        )
                    )
                    .limit(1)
                    .consistentRead(true)
            }
            (resp.count() ?: 0) > 0
        } catch (e: Exception) {
            log.error(e) { "[existsCommit] DynamoDB 조회 실패 repoId=$repositoryId, commitHash=$commitHash" }
            false
        }
    }
}
