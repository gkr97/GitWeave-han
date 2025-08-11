package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexCache
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobStorageReader
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

@Service
class FileContentService(
    private val cache: GitIndexCache,
    private val s3BlobStorageReader: S3BlobStorageReader,
    private val s3Presigner: S3Presigner,
    @Value("\${cloud.aws.s3.bucket}") private val bucket: String,
    @Value("\${cloud.aws.s3.endpoint}") private val s3Endpoint: String,
    @Value("\${cloud.aws.s3.public-endpoint:}") private val s3PublicEndpoint: String?
) {
    private val maxTextBytes: Long = 1_048_576

    /**
     * 파일 내용 조회
     *
     * @param repositoryId 레포지토리 ID
     * @param commitHash 커밋 해시 (필수)
     * @param path 파일 경로 (상대 경로, 슬래시로 시작하지 않음)
     * @param branch 브랜치 이름 (선택적, null이면 HEAD)
     * @return 파일 내용 응답
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

            // 2) 판정/키 정규화
            val isLargeText = (blobMeta.size ?: 0) > maxTextBytes
            val isBinary = blobMeta.isBinary || isLargeText
            val objectKey = if (blobMeta.externalStorageKey.startsWith("blobs/"))
                blobMeta.externalStorageKey else "blobs/${blobMeta.externalStorageKey}"

            log.info {
                "[FileContent] 메타 ok repoId=$repositoryId, path=$normPath, size=${blobMeta.size}, " +
                        "mime=${blobMeta.mimeType}, isBinary=${blobMeta.isBinary}, largeText=$isLargeText, key=$objectKey"
            }

            // 3) presign or 본문 로드 ( view raw content )
            val (downloadUrl, expiresAt) =
                if (isBinary) presign(objectKey, normPath, blobMeta.mimeType)
                    .let { rewriteIfNeeded(it.first) to it.second }
                else null to null

            val content: String? =
                if (!isBinary) {
                    val t = measureTimeMillis {
                        s3BlobStorageReader.readBlobAsString(objectKey.removePrefix("blobs/"))
                    }
                    log.info { "[FileContent] 텍스트 본문 로드 완료 path=$normPath, elapsed=${t}ms" }
                    s3BlobStorageReader.readBlobAsString(objectKey.removePrefix("blobs/"))
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

            val resp = FileContentResponse(
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
            _lastResponse = resp
        }
        log.info { "[FileContent] 처리 완료 path=$normPath, elapsed=${elapsedMetaMs}ms" }

        return _lastResponse!!.also { _lastResponse = null }
    }

    @Volatile private var _lastResponse: FileContentResponse? = null

    /**
     * S3 객체를 presign하여 다운로드 URL을 생성합니다.
     * @param objectKey S3 객체 키
     * @param filename 다운로드 시 표시될 파일 이름
     * @param mimeType MIME 타입 (선택적)
     * @return presigned URL과 만료 시간 문자열
     */
    private fun presign(objectKey: String, filename: String, mimeType: String?): Pair<String, String> {
        val getReq = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .responseContentDisposition("""attachment; filename="$filename"""")
            .responseContentType(mimeType ?: "application/octet-stream")
            .build()

        val duration = Duration.ofMinutes(10)
        val preReq = GetObjectPresignRequest.builder()
            .signatureDuration(duration)
            .getObjectRequest(getReq)
            .build()

        val t = measureTimeMillis {
            val signed = s3Presigner.presignGetObject(preReq)
            val expiresAt = Instant.now().plus(duration).toString()
            log.info { "[FileContent] presign 성공 bucket=$bucket, key=$objectKey, ttlMin=${duration.toMinutes()}, expiresAt=$expiresAt" }
            _presignPair = signed.url().toString() to expiresAt
        }
        log.info { "[FileContent] presign elapsed=${t}ms, key=$objectKey" }

        return _presignPair!!.also { _presignPair = null }
    }

    @Volatile private var _presignPair: Pair<String, String>? = null

    /**
     * presigned URL의 호스트를 S3 퍼블릭 엔드포인트로 치환합니다.
     * @param url 원본 presigned URL
     * @return 치환된 URL
     */
    private fun rewriteIfNeeded(url: String): String {
        val pub = s3PublicEndpoint?.takeIf { it.isNotBlank() } ?: return url
        val src = URI.create(s3Endpoint)
        val dst = URI.create(pub)
        val u = URI.create(url)
        val replaced = URI(
            u.scheme, u.userInfo,
            dst.host, if (dst.port != -1) dst.port else u.port,
            u.path, u.query, u.fragment
        )
        if (src.host != dst.host) {
            log.info { "[FileContent] presigned URL 호스트 치환 ${src.host}:${src.port} -> ${dst.host}:${dst.port}" }
        }
        return replaced.toString()
    }
}
