package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.dto.FileMeta
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

    fun getBlobMeta(
        repoId: Long,
        fileHash: String
    ): FileMeta? {
        return try {
            val sk = "BLOB#$fileHash"
            val response = dynamoDbClient.getItem {
                it.tableName(tableName)
                    .key(
                        mapOf(
                            "PK" to AttributeValue.fromS("REPO#$repoId"),
                            "SK" to AttributeValue.fromS(sk)
                        )
                    )
            }
            if (!response.hasItem()) return null
            val item = response.item()
            FileMeta(
                path = item["path"]?.s() ?: "",
                externalStorageKey = item["external_storage_key"]?.s() ?: return null,
                isBinary = item["is_binary"]?.bool() ?: false,
                mimeType = item["mime_type"]?.s(),
                size = item["file_size"]?.n()?.toLongOrNull()
            )
        } catch (e: Exception) {
            log.error(e) { "[getBlobMeta] DynamoDB 메타 조회 실패 repoId=$repoId, fileHash=$fileHash" }
            null
        }
    }


}
