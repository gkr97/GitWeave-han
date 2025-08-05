package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.BlobTree
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant

@Repository
class TreeCommandRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    fun save(tree: BlobTree) {
        log.info { "[saveTree] 저장 시작: repoId=${tree.repositoryId}, path=${tree.path.value}, commit=${tree.commitHash.value}" }

        val item = mapOf(
            "PK" to AttributeValue.fromS("REPO#${tree.repositoryId}"),
            "SK" to AttributeValue.fromS("TREE#${tree.commitHash.value}#${tree.path.value}"),
            "type" to AttributeValue.fromS("tree"),
            "created_at" to AttributeValue.fromS(tree.lastModifiedAt?.toString() ?: Instant.now().toString()),
            "path" to AttributeValue.fromS(tree.path.value),
            "commit_hash" to AttributeValue.fromS(tree.commitHash.value),
            "file_hash" to AttributeValue.fromS(tree.fileHash ?: ""),
            "name" to AttributeValue.fromS(tree.name),
            "is_directory" to AttributeValue.fromBool(tree.isDirectory),
            "size" to AttributeValue.fromN(tree.size?.toString() ?: "0"),
            "depth" to AttributeValue.fromN(tree.depth.toString())
        )

        dynamoDbClient.putItem { it.tableName(tableName).item(item) }

        log.info { "[saveTree] 저장 완료: repoId=${tree.repositoryId}, path=${tree.path.value}, commit=${tree.commitHash.value}" }
    }
}
