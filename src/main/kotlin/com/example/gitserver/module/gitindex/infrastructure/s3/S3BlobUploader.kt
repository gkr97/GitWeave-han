package com.example.gitserver.module.gitindex.infrastructure.s3

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest

private val log = mu.KotlinLogging.logger {}

@Component
class S3BlobUploader(
    private val s3Client: S3Client,
    @Value("\${cloud.aws.s3.bucket}") private val bucketName: String
) {
    /**
     * S3에 Blob을 "전역 해시" 기반으로 저장합니다.
     * 이미 존재하는 blob은 중복 저장하지 않습니다.
     * @param hash Blob 해시
     * @param bytes Blob 데이터
     * @return S3에 저장된 Blob의 key
     */
    fun upload(hash: String, bytes: ByteArray): String {
        val key = "blobs/$hash"

        val alreadyExists = try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            )
            log.info { "이미 존재하는 blob, 업로드 생략 - hash=$hash, key=$key" }
            true
        } catch (e: Exception) {
            false
        }

        if (!alreadyExists) {
            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentLength(bytes.size.toLong())
                .build()

            s3Client.putObject(
                putRequest,
                RequestBody.fromBytes(bytes)
            )
            log.info { "새로운 blob 업로드 완료 - hash=$hash, key=$key, size=${bytes.size} bytes" }
        }

        return key
    }
}
