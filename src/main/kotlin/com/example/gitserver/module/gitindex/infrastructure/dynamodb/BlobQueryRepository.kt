package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@Repository
class BlobQueryRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    fun countBlobsByExtension(repoId: Long): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        var lastEvaluatedKey: Map<String, AttributeValue>? = null

        do {
            val response = dynamoDbClient.query {
                it.tableName(tableName)
                    .keyConditionExpression("PK = :pk and begins_with(SK, :skPrefix)")
                    .expressionAttributeValues(
                        mapOf(
                            ":pk" to AttributeValue.fromS("REPO#$repoId"),
                            ":skPrefix" to AttributeValue.fromS("BLOB#")
                        )
                    )
                    .exclusiveStartKey(lastEvaluatedKey)
            }

            response.items().forEach {
                val ext = it["extension"]?.s()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@forEach
                result[ext] = result.getOrDefault(ext, 0) + 1
            }

            lastEvaluatedKey = response.lastEvaluatedKey()
        } while (!lastEvaluatedKey.isNullOrEmpty())

        return result
    }
}
