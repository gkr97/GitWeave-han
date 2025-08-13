package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.Put
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException
import java.time.Instant
import java.util.UUID

@Repository
class GitIndexTxRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    /**
     * 블롭과 블롭 트리를 원자적으로 저장합니다.
     * - 블롭: BLOB#<hash>
     * - 트리: TREE#<commitHash>#<path>
     *
     * @param blob 저장할 블롭 객체
     * @param tree 저장할 블롭 트리 객체
     */
    fun saveBlobAndTree(blob: Blob, tree: BlobTree) {
        val blobItem = toBlobItem(blob)
        val treeItem = toTreeItem(tree)

        val blobPut = Put.builder()
            .tableName(tableName)
            .item(blobItem)
            .build()

        val treePut = Put.builder()
            .tableName(tableName)
            .item(treeItem)
            .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
            .build()

        val txItems = listOf(
            TransactWriteItem.builder().put(blobPut).build(),
            TransactWriteItem.builder().put(treePut).build()
        )

        log.info {
            "[GitIndexTxRepository] atomic write 시작 - " +
                    "repoId=${blob.repositoryId}, blob=${blob.hash.value}, path=${tree.path.value}, commit=${tree.commitHash.value}"
        }

        try {
            dynamoDbClient.transactWriteItems { b ->
                b.transactItems(txItems)
                    .clientRequestToken(UUID.randomUUID().toString())
            }
            log.info {
                "[GitIndexTxRepository] atomic write 완료 - " +
                        "repoId=${blob.repositoryId}, blob=${blob.hash.value}, path=${tree.path.value}"
            }
        } catch (e: TransactionCanceledException) {
            val reasons = e.cancellationReasons()
            val blobReason = reasons.getOrNull(0)?.code()
            val treeReason = reasons.getOrNull(1)?.code()

            val isIdempotentTreeAlreadyExists =
                (blobReason == null || blobReason == "None") &&
                        (treeReason == "ConditionalCheckFailed")

            if (isIdempotentTreeAlreadyExists) {
                log.info {
                    "[GitIndexTxRepository] atomic write 멱등성 성공 처리 - " +
                            "Tree가 이미 존재함 (repoId=${blob.repositoryId}, path=${tree.path.value}, commit=${tree.commitHash.value})"
                }
                return
            }

            log.warn(e) {
                "[GitIndexTxRepository] atomic write 실패 - " +
                        "reasons=$reasons, repoId=${blob.repositoryId}, blob=${blob.hash.value}, path=${tree.path.value}, commit=${tree.commitHash.value}"
            }
            throw e
        }
    }

    private fun toBlobItem(blob: Blob): Map<String, AttributeValue> {
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
        return item
    }

    private fun toTreeItem(tree: BlobTree): Map<String, AttributeValue> =
        mapOf(
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
}
