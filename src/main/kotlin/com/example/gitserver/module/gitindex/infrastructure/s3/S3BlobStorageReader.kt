package com.example.gitserver.module.gitindex.infrastructure.s3

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.nio.charset.StandardCharsets

@Component
class S3BlobStorageReader(
    private val s3Client: S3Client,
    @Value("\${cloud.aws.s3.bucket}") private val bucketName: String
) {
    private val log = KotlinLogging.logger {}

    /**
     * S3에서 블롭을 읽어 문자열로 반환합니다.
     * @param repoId 레포지토리 ID
     * @param blobHash 블롭 해시
     * @return 블롭의 내용 문자열, 실패 시 null
     */
    fun readBlobAsString(repoId: Long, blobHash: String): String? {
        val key = "blobs/$repoId/$blobHash"
        return try {
            val response = s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            )
            response.readAllBytes().toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            log.warn(e) { "[S3BlobStorageReader] Blob 로딩 실패 - key=$key" }
            null
        }
    }
}

