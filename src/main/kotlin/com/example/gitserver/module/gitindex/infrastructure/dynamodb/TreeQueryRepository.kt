package com.example.gitserver.module.gitindex.infrastructure.dynamodb

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.domain.dto.TreeItem
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
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

    /**
     * 경로를 정규화합니다.
     * - null 또는 빈 문자열은 null로 변환
     * - 앞뒤 공백 제거
     * - 앞뒤 슬래시 제거
     */
    private fun normalizePath(path: String?): String? {
        val p = path?.trim()?.trim('/')
        return if (p.isNullOrEmpty()) null else p
    }

    /**
     * 특정 커밋 해시와 브랜치에 대한 루트 디렉토리의 파일 트리 조회
     * @param branch null이면 기본 브랜치
     */
    fun getFileTreeAtRoot(repoId: Long, commitHash: String, branch: String?): List<TreeNodeResponse> {
        val treePrefix = "TREE#$commitHash#"

        return try {
            val commitItem = getCommitItem(repoId, commitHash, branch)

            val response = dynamoDbClient.query {
                it.tableName(tableName)
                    .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                    .expressionAttributeValues(
                        mapOf(
                            ":pk" to AttributeValue.fromS("REPO#$repoId"),
                            ":skPrefix" to AttributeValue.fromS(treePrefix)
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
                        size = item["size"]?.n()?.toLongOrNull(),
                        lastCommitHash = item["commit_hash"]?.s() ?: commitHash,
                        lastCommitMessage = commitItem?.get("message")?.s(),
                        lastCommittedAt = commitItem?.get("committed_at")?.s(),
                        lastCommitter = commitItem?.let { c ->
                            RepositoryUserResponse(
                                userId = c["author_id"]?.n()?.toLongOrNull() ?: -1,
                                nickname = c["author_name"]?.s() ?: "unknown",
                                profileImageUrl = c["author_profile_image_url"]?.s()
                            )
                        }
                    )
                }
        } catch (e: Exception) {
            log.error(e) { "[getFileTreeAtRoot] DynamoDB 조회 실패 - repoId=$repoId, commitHash=$commitHash" }
            emptyList()
        }
    }

    /**
     * 특정 커밋 해시와 경로에 대한 파일/폴더 트리 조회
     * @param path null이면 루트 디렉토리
     */
    fun getFileTree(repoId: Long, commitHash: String, path: String?, branch: String?): List<TreeNodeResponse> {
        val normPath = normalizePath(path)

        val treePrefix = if (normPath == null) {
            "TREE#$commitHash#"
        } else {
            "TREE#$commitHash#$normPath/"
        }

        val response = dynamoDbClient.query {
            it.tableName(tableName)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.fromS("REPO#$repoId"),
                        ":skPrefix" to AttributeValue.fromS(treePrefix)
                    )
                )
        }

        val depth = if (normPath == null) 0 else normPath.count { it == '/' } + 1

        return response.items()
            .filter { it["depth"]?.n() == depth.toString() }
            .mapNotNull { item ->
                val sk = item["SK"]?.s() ?: return@mapNotNull null
                val childPath = sk.split("#", limit = 3).getOrNull(2) ?: return@mapNotNull null

                val lastCommitHash = item["commit_hash"]?.s() ?: commitHash

                val commitItem = getCommitItem(repoId, lastCommitHash, branch)
                val committer = commitItem?.let { c ->
                    RepositoryUserResponse(
                        userId = c["author_id"]?.n()?.toLongOrNull() ?: -1,
                        nickname = c["author_name"]?.s() ?: "unknown",
                        profileImageUrl = c["author_profile_image_url"]?.s()
                    )
                }

                TreeNodeResponse(
                    name = item["name"]?.s() ?: return@mapNotNull null,
                    path = childPath,
                    isDirectory = item["is_directory"]?.bool() ?: false,
                    size = item["size"]?.n()?.toLongOrNull(),
                    lastCommitHash = lastCommitHash,
                    lastCommitMessage = commitItem?.get("message")?.s(),
                    lastCommittedAt = commitItem?.get("committed_at")?.s(),
                    lastCommitter = committer
                )
            }
    }

    /**
     * 특정 커밋 해시와 경로에 대한 트리 아이템 조회
     * @param path null이면 루트
     */
    fun getTreeItem(
        repoId: Long,
        commitHash: String,
        path: String
    ): TreeItem? {
        return try {
            val sk = "TREE#$commitHash#$path"
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
            TreeItem(
                path = path,
                fileHash = item["file_hash"]?.s(),
            )
        } catch (e: Exception) {
            log.error(e) { "[getTreeItem] DynamoDB 트리 조회 실패 repoId=$repoId, commitHash=$commitHash, path=$path" }
            null
        }
    }


    /**
     * 특정 커밋 해시와 브랜치에 대한 커밋 아이템 조회
     * @param branch null이면 기본 브랜치
     */
    fun getCommitItem(repoId: Long, commitHash: String, branch: String?): Map<String, AttributeValue>? {
        val ref = GitRefUtils.toFullRefOrNull(branch)
        val sk = if (ref != null) {
            "COMMIT#$commitHash#$ref"
        } else {
            "COMMIT#$commitHash"
        }
        return try {
            val result = dynamoDbClient.getItem {
                it.tableName(tableName)
                    .key(
                        mapOf(
                            "PK" to AttributeValue.fromS("REPO#$repoId"),
                            "SK" to AttributeValue.fromS(sk)
                        )
                    )
            }
            if (result.hasItem()) result.item() else null
        } catch (e: Exception) {
            log.error(e) { "[getCommitItem] DynamoDB 조회 실패 - repoId=$repoId, commitHash=$commitHash, branch=$branch" }
            null
        }
    }
}
