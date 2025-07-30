package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.module.gitindex.domain.Commit
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@Repository
class CommitDynamoRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.gitIndexTable}") private val tableName: String
) {
    private val log = KotlinLogging.logger {}

    fun save(commit: Commit) {
        log.info { "[saveCommit] 저장 시작: repoId=${commit.repositoryId}, hash=${commit.hash.value}" }

        val item = mutableMapOf(
            "PK" to AttributeValue.fromS("REPO#${commit.repositoryId}"),
            "SK" to AttributeValue.fromS("COMMIT#${commit.hash.value}#${commit.branch}"),
            "type" to AttributeValue.fromS("commit"),
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

        commit.authorId?.let {
            item["author_id"] = AttributeValue.fromN(it.toString())
        }

        dynamoDbClient.putItem { it.tableName(tableName).item(item) }

        log.info { "[saveCommit] 저장 완료: repoId=${commit.repositoryId}, hash=${commit.hash.value}" }
    }
}
