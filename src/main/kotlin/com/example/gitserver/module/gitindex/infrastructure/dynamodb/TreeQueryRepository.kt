package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@Repository
class TreeQueryRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    fun getFileTreeAtRoot(repoId: Long, commitHash: String): List<TreeNodeResponse> {
        val prefix = "TREE#$commitHash#"

        return try {
            val response = dynamoDbClient.query {
                it.tableName(tableName)
                    .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                    .expressionAttributeValues(
                        mapOf(
                            ":pk" to AttributeValue.fromS("REPO#$repoId"),
                            ":skPrefix" to AttributeValue.fromS(prefix)
                        )
                    )
            }

            response.items()
                .filter { it["depth"]?.n() == "0" }
                .mapNotNull { item ->
                    val sk = item["SK"]?.s() ?: return@mapNotNull null
                    val path = sk.split("#", limit = 3).getOrNull(2) ?: return@mapNotNull null

                    TreeNodeResponse(
                        name = item["name"]?.s() ?: return@mapNotNull null,
                        path = path,
                        isDirectory = item["is_directory"]?.bool() ?: false,
                        size = item["size"]?.n()?.toLongOrNull()
                    )
                }
        } catch (e: Exception) {
            log.error(e) { "[getFileTreeAtRoot] DynamoDB 조회 실패 - repoId=$repoId, commitHash=$commitHash" }
            emptyList()
        }
    }
}
