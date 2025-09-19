package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.port.TreeRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant

@Repository
class DynamoTreeAdapter(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) : TreeRepository {
    private val log = KotlinLogging.logger {}

    /**
     * 블롭 트리를 저장합니다.
     * - PK: REPO#<repositoryId>
     * - SK: TREE#<commitHash>#<path>
     *
     * @param tree 저장할 블롭 트리 객체
     */
    override fun save(tree: BlobTree) {
        log.info { "[saveTree] 저장 시작: repoId=${tree.repositoryId}, path=${tree.path.value}, commit=${tree.commitHash.value}" }

        val item = mutableMapOf(
            "PK" to AttributeValue.fromS("REPO#${tree.repositoryId}"),
            "SK" to AttributeValue.fromS("TREE#${tree.commitHash.value}#${tree.path.value}"),
            "type" to AttributeValue.fromS("tree"),
            "created_at" to AttributeValue.fromS(tree.lastModifiedAt?.toString() ?: Instant.now().toString()),
            "path" to AttributeValue.fromS(tree.path.value),
            "commit_hash" to AttributeValue.fromS(tree.commitHash.value),
            "name" to AttributeValue.fromS(tree.name),
            "is_directory" to AttributeValue.fromBool(tree.isDirectory),
            "size" to AttributeValue.fromN(tree.size?.toString() ?: "0"),
            "depth" to AttributeValue.fromN(tree.depth.toString())
        )

        tree.fileHash?.takeIf { it.isNotBlank() }?.let {
            item["file_hash"] = AttributeValue.fromS(it)
        }

        dynamoDbClient.putItem { it.tableName(tableName).item(item) }

        log.info { "[saveTree] 저장 완료: repoId=${tree.repositoryId}, path=${tree.path.value}, commit=${tree.commitHash.value}" }
    }
}
