package com.example.gitserver.module.gitindex.infrastructure.s3

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

private val log = mu.KotlinLogging.logger {}

@Component
class S3BlobUploader(
    private val s3Client: S3Client,
    @Value("\${cloud.aws.s3.bucket}") private val bucketName: String
) {
    /**
     * S3에 Blob 데이터를 업로드합니다.
     * @param repositoryId 저장소 ID
     * @param hash Blob 해시
     * @param bytes Blob 데이터
     * @return S3에 저장된 Blob의 키
     */
    fun upload(repositoryId: Long, hash: String, bytes: ByteArray): String {
        log.info { "업로드 시작 $repositoryId blob to $hash" }
        val key = "blobs/$repositoryId/$hash"

        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentLength(bytes.size.toLong())
            .build()

        s3Client.putObject(
            putRequest,
            RequestBody.fromBytes(bytes)
        )

        log.info { "블롭 업로드 S3 - repositoryId=$repositoryId, hash=$hash, key=$key" }
        return key

    }
}
