package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.Blob
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@Repository
class BlobDynamoRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    fun save(blob: Blob) {
        log.info { "[saveBlob] 저장 시작: repoId=${blob.repositoryId}, hash=${blob.hash.value}" }

        val item = mutableMapOf(
            "PK" to AttributeValue.fromS("REPO#${blob.repositoryId}"),
            "SK" to AttributeValue.fromS("BLOB#${blob.hash.value}"),
            "type" to AttributeValue.fromS("blob"),
            "created_at" to AttributeValue.fromS(blob.createdAt.toString()),
            "hash" to AttributeValue.fromS(blob.hash.value),
            "path" to AttributeValue.fromS(blob.path?.value ?: ""),
            "mime_type" to AttributeValue.fromS(blob.mimeType ?: ""),
            "is_binary" to AttributeValue.fromBool(blob.isBinary),
            "file_size" to AttributeValue.fromN(blob.fileSize.toString()),
            "line_count" to AttributeValue.fromN(blob.lineCount?.toString() ?: "0"),
            "external_storage_key" to AttributeValue.fromS(blob.externalStorageKey)
        )

        blob.extension?.takeIf { it.isNotBlank() }?.let {
            item["extension"] = AttributeValue.fromS(it)
        }

        dynamoDbClient.putItem { it.tableName(tableName).item(item) }

        log.info { "[saveBlob] 저장 완료: repoId=${blob.repositoryId}, hash=${blob.hash.value}" }
    }
}
