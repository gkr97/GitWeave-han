package com.example.gitserver.module.gitindex.application.query

import com.example.gitserver.module.gitindex.domain.port.BlobObjectStorage
import com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexCache
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.ZoneId
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

@Service
class FileContentQueryService(
    private val cache: GitIndexCache,
    private val blobStorage: BlobObjectStorage,
) {
    private val maxTextBytes: Long = 1_048_576

    /**
     * 파일 내용 조회
     *
     * @param repositoryId 레포지토리 ID
     * @param commitHash 커밋 해시 (필수)
     * @param path 파일 경로 (상대 경로, 슬래시로 시작하지 않음)
     * @param branch 브랜치 이름 (선택적, null이면 HEAD)
     */
    fun getFileContent(
        repositoryId: Long,
        commitHash: String?,
        path: String,
        branch: String?
    ): FileContentResponse {
        val hash = requireNotNull(commitHash) { "commitHash는 필수입니다." }
        val normPath = path.trim().trim('/')

        log.info { "[FileContent] 요청 수신 repoId=$repositoryId, commit=$hash, path=$normPath, branch=$branch" }

        val elapsedMetaMs = measureTimeMillis {
            // 1) 메타 조회
            val commit = cache.getCommitByHash(repositoryId, hash)
                ?: run {
                    log.warn { "[FileContent] 커밋 메타 없음 repoId=$repositoryId, commit=$hash" }
                    throw IllegalStateException("커밋 메타 없음: $hash")
                }

            val treeItem = cache.getTreeItem(repositoryId, hash, normPath)
                ?: run {
                    log.warn { "[FileContent] 파일 트리 메타 없음 repoId=$repositoryId, commit=$hash, path=$normPath" }
                    throw NoSuchElementException("파일 트리 메타 없음: $normPath")
                }

            val fileHash = treeItem.fileHash
                ?: run {
                    log.error { "[FileContent] file_hash 없음 repoId=$repositoryId, commit=$hash, path=$normPath" }
                    throw IllegalStateException("file_hash 없음: $normPath")
                }

            val blobMeta = cache.getBlobMeta(repositoryId, fileHash)
                ?: run {
                    log.warn { "[FileContent] Blob 메타 없음 repoId=$repositoryId, fileHash=$fileHash, path=$normPath" }
                    throw NoSuchElementException("Blob 메타 없음: $fileHash")
                }

            // 2) 텍스트/바이너리 판정 및 키 정규화
            val isLargeText = (blobMeta.size ?: 0) > maxTextBytes
            val isBinary = blobMeta.isBinary || isLargeText
            val objectKey = if (blobMeta.externalStorageKey.startsWith("blobs/"))
                blobMeta.externalStorageKey else "blobs/${blobMeta.externalStorageKey}"

            log.info {
                "[FileContent] 메타 ok repoId=$repositoryId, path=$normPath, size=${blobMeta.size}, " +
                        "mime=${blobMeta.mimeType}, isBinary=${blobMeta.isBinary}, largeText=$isLargeText, key=$objectKey"
            }

            // 3) presign or 본문 로드 (view raw content)
            val (downloadUrl, expiresAt) =
                if (isBinary) blobStorage.presignForDownload(objectKey, normPath, blobMeta.mimeType)
                else null to null

            val content: String? =
                if (!isBinary) {
                    val t = measureTimeMillis {
                        blobStorage.readAsString(objectKey)
                    }
                    log.info { "[FileContent] 텍스트 본문 로드 완료 path=$normPath, elapsed=${t}ms" }
                    blobStorage.readAsString(objectKey)
                } else {
                    log.info { "[FileContent] 바이너리/대용량 처리 path=$normPath, presignedOnly=true" }
                    null
                }

            val committer: RepositoryUserResponse = commit.author.let {
                RepositoryUserResponse(
                    userId = it.userId,
                    nickname = it.nickname,
                    profileImageUrl = it.profileImageUrl
                )
            }

            val committedAtStr = try {
                commit.committedAt.atZone(ZoneId.systemDefault()).toInstant().toString()
            } catch (e: Exception) {
                log.warn(e) { "[FileContent] committedAt 변환 실패 commit=$hash, path=$normPath" }
                null
            }

            _lastResponse = FileContentResponse(
                path = normPath,
                content = content,
                isBinary = isBinary,
                mimeType = blobMeta.mimeType,
                size = blobMeta.size,
                commitHash = hash,
                commitMessage = commit.message,
                committedAt = committedAtStr,
                committer = committer,
                downloadUrl = downloadUrl,
                expiresAt = expiresAt
            )

            if (downloadUrl != null) {
                log.info { "[FileContent] presigned 준비 완료 path=$normPath, key=$objectKey, expiresAt=$expiresAt" }
            }
        }
        log.info { "[FileContent] 처리 완료 path=$normPath, elapsed=${elapsedMetaMs}ms" }

        return _lastResponse!!.also { _lastResponse = null }
    }

    @Volatile private var _lastResponse: FileContentResponse? = null
}