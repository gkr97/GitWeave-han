package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.repository.interfaces.dto.ReadmeResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@Component
class ReadmeQueryRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    fun findReadmeBlobInfo(repoId: Long, commitHash: String): Pair<String, String?>? {
        val response = dynamoDbClient.query {
            it.tableName(tableName)
                .indexName("GSI_BlobTree_By_Commit")
                .keyConditionExpression("commit_hash = :commitHash")
                .expressionAttributeValues(
                    mapOf(":commitHash" to AttributeValue.fromS(commitHash))
                )
        }

        return response.items()
            .asSequence()
            .filter { it["PK"]?.s() == "REPO#$repoId" }
            .mapNotNull { item ->
                val sk = item["SK"]?.s() ?: return@mapNotNull null
                val path = sk.split("#", limit = 3).getOrNull(2) ?: return@mapNotNull null
                if (!path.split("/").last().matches(Regex("(?i)^README\\..*"))) return@mapNotNull null

                val blobHash = item["blob_hash"]?.s() ?: item["file_hash"]?.s()
                path to blobHash
            }
            .firstOrNull()
    }
}
