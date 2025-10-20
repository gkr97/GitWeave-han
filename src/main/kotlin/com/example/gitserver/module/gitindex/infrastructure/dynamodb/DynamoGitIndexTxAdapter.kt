package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.domain.port.IndexTxRepository
import com.example.gitserver.module.gitindex.domain.vo.CommitHash
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

private val log = KotlinLogging.logger {}

@Repository
class DynamoGitIndexTxAdapter(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) : IndexTxRepository {

    /**
     * 블럭만 저장합니다.
     */
    override fun saveBlobOnly(blob: Blob) {
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
    }

    /**
     * 블롭과 블롭 트리를 원자적으로 저장합니다.
     * - 블롭은 덮어쓰기 가능
     * - 블롭 트리는 이미 존재하면 저장하지 않음
     * - 최대 5회 재시도(지수 백오프 + 지터)
     */
    override fun saveBlobAndTree(blob: Blob, tree: BlobTree) {
        val blobPut = Put.builder()
            .tableName(tableName)
            .item(toBlobItem(blob))
            .build()

        val treePut = Put.builder()
            .tableName(tableName)
            .item(toTreeItem(tree))
            .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
            .build()

        val txItems = listOf(
            TransactWriteItem.builder().put(blobPut).build(),
            TransactWriteItem.builder().put(treePut).build()
        )

        val maxRetries = 5
        var attempt = 0
        while (true) {
            try {
                dynamoDbClient.transactWriteItems { b ->
                    b.transactItems(txItems)
                        .clientRequestToken(UUID.randomUUID().toString())
                }
                return
            } catch (e: TransactionCanceledException) {
                val codes = e.cancellationReasons()?.mapNotNull { it?.code() } ?: emptyList()
                if (codes.isNotEmpty() && codes.all { it == "ConditionalCheckFailed" }) {
                    log.debug { "[DDB] saveBlobAndTree idempotent (TREE exists). blob=${blob.hash.value}, path=${tree.path.value}" }
                    return
                }
                attempt++
                if (attempt >= maxRetries) throw e
                val base = 50L * (1L shl (attempt - 1))
                val jitter = ThreadLocalRandom.current().nextLong(0, 50)
                log.debug { "[DDB] saveBlobAndTree retrying... attempt=$attempt, errCodes=$codes" }
                Thread.sleep((base + jitter).coerceAtMost(1000))
            }
        }
    }

    /**
     * 커밋을 sealed=false 상태로 먼저 저장합니다.
     * (필요 시 조건부 put으로 멱등화 가능)
     */
    override fun prepareCommit(commit: Commit) {
        val item = mutableMapOf(
            "PK" to AttributeValue.fromS("REPO#${commit.repositoryId}"),
            "SK" to AttributeValue.fromS("COMMIT#${commit.hash.value}#${commit.branch}"),
            "type" to AttributeValue.fromS("commit"),
            "sealed" to AttributeValue.fromBool(false),
            "created_at" to AttributeValue.fromS(commit.createdAt.toString()),
            "committed_at" to AttributeValue.fromS(commit.committedAt.toString()),
            "branch" to AttributeValue.fromS(commit.branch),
            "message" to AttributeValue.fromS(commit.message),
            "author_name" to AttributeValue.fromS(commit.authorName),
            "author_email" to AttributeValue.fromS(commit.authorEmail),
            "committer_name" to AttributeValue.fromS(commit.committerName),
            "committer_email" to AttributeValue.fromS(commit.committerEmail),
            "tree_hash" to AttributeValue.fromS(commit.treeHash.value),
            "parent_hashes" to AttributeValue.fromL(commit.parentHashes.map { AttributeValue.fromS(it) })
        )
        commit.authorId?.let { item["author_id"] = AttributeValue.fromN(it.toString()) }

        try {
            dynamoDbClient.putItem {
                it.tableName(tableName).item(item)
                  .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
            }
        } catch (_: ConditionalCheckFailedException) {
        }
    }

    /**
     * 커밋을 sealed=true로 확정하고 브랜치 ref를 CAS 방식으로 갱신합니다.
     * - expectedOld가 null이면 브랜치가 없을 때만 갱신 시도
     * - 최대 5회 재시도(지수 백오프 + 지터)
     * - ref가 이미 커밋 해시를 가리키고 있으면 성공으로 간주
     * - 실패 시 false 반환
     */
    override fun sealCommitAndUpdateRef(
        repoId: Long,
        branch: String,
        commitHash: CommitHash,
        expectedOld: CommitHash?
    ): Boolean {
        val normBranch = normalizeBranch(branch)
        val maxRetries = 5
        var attempt = 0
        var expect = expectedOld

        while (true) {
            if (trySealAndUpdateOnce(repoId, normBranch, commitHash, expect)) {
                return true
            }

            val current = readRefHead(repoId, normBranch)
            if (current?.value == commitHash.value) {
                log.debug { "[DDB] ref already advanced repo=$repoId branch=$normBranch head=${commitHash.value}" }
                return true
            }
            if (expect != null) {
                expect = current
            }

            attempt++
            if (attempt >= maxRetries) return false

            val base = 50L * (1L shl (attempt - 1))
            val jitter = ThreadLocalRandom.current().nextLong(0, 50)
            Thread.sleep((base + jitter).coerceAtMost(1000))
        }
    }

    /**
     * 커밋을 sealed=true로 확정하고 브랜치 ref를 CAS 방식으로 갱신을 시도합니다.
     * - 실패 시 예외를 throw하지 않고 false를 반환합니다.
     */
    private fun trySealAndUpdateOnce(
        repoId: Long,
        normBranch: String,
        commitHash: CommitHash,
        expectedOld: CommitHash?
    ): Boolean {
        val commitKey = mapOf(
            "PK" to AttributeValue.fromS("REPO#$repoId"),
            "SK" to AttributeValue.fromS("COMMIT#${commitHash.value}#$normBranch")
        )
        val refKey = mapOf(
            "PK" to AttributeValue.fromS("REPO#$repoId"),
            "SK" to AttributeValue.fromS("REF#$normBranch")
        )

        val markSealed = TransactWriteItem.builder().update {
            it.tableName(tableName)
                .key(commitKey)
                .updateExpression("SET sealed = :t")
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .expressionAttributeValues(mapOf(":t" to AttributeValue.fromBool(true)))
        }.build()

        val vals = mutableMapOf<String, AttributeValue>(
            ":new" to AttributeValue.fromS(commitHash.value)
        )

        val refUpdate = TransactWriteItem.builder().update {
            it.tableName(tableName)
                .key(refKey)
                .updateExpression("SET commit_hash = :new")
                .apply {
                    if (expectedOld != null) {
                        vals[":old"] = AttributeValue.fromS(expectedOld.value)
                        it.conditionExpression("commit_hash = :old")
                    } else {
                        it.conditionExpression("attribute_not_exists(commit_hash)")
                    }
                }
                .expressionAttributeValues(vals)
        }.build()

        return try {
            dynamoDbClient.transactWriteItems { b ->
                b.transactItems(markSealed, refUpdate)
                    .clientRequestToken(UUID.randomUUID().toString())
            }
            true
        } catch (e: TransactionCanceledException) {
            val codes = e.cancellationReasons()?.mapNotNull { it?.code() }?.toSet() ?: emptySet()
            val retryable =
                codes.isEmpty() ||
                        codes.any { it.equals("ConditionalCheckFailed", true) } || // CAS 실패
                        codes.any { it.equals("TransactionConflict", true) || it.equals("TransactionInProgress", true) }
            if (retryable) {
                log.debug { "[DDB] seal/ref retry candidate repo=$repoId branch=$normBranch expect=${expectedOld?.value} errCodes=$codes" }
                return false
            }
            throw e
        }
    }

    /**
     * 브랜치의 현재 HEAD 커밋 해시를 조회합니다.
     */
    private fun readRefHead(repoId: Long, normBranch: String): CommitHash? {
        val key = mapOf(
            "PK" to AttributeValue.fromS("REPO#$repoId"),
            "SK" to AttributeValue.fromS("REF#$normBranch")
        )
        val resp = dynamoDbClient.getItem {
            it.tableName(tableName).key(key).consistentRead(true)
        }
        val v = resp.item()["commit_hash"]?.s()
        return v?.let { CommitHash(it) }
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

    private fun toTreeItem(tree: BlobTree): Map<String, AttributeValue> {
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
        return item
    }

    private fun normalizeBranch(input: String) =
        if (input.startsWith("refs/heads/")) input else "refs/heads/$input"
}
