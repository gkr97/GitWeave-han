package com.example.gitserver.module.user.infrastructure.s3

import com.example.gitserver.module.user.exception.S3FileException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

@Service
class S3Uploader(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @Value("\${cloud.aws.s3.bucket}") private val bucket: String
) {

    /**
     * 이미지 파일을 S3에 업로드하고 공개 URL을 반환합니다.
     */
    fun upload(file: MultipartFile, fixedKey: String): String {
        val ext = getExtension(file.originalFilename ?: "")
        validateImageExtension(ext)
        validateFileSize(file)
        validateContentType(file, ext)

        val put = PutObjectRequest.builder()
            .bucket(bucket)
            .key(fixedKey)
            .contentType(file.contentType)
            .cacheControl("public, max-age=31536000, immutable")
            .acl(ObjectCannedACL.PUBLIC_READ)
            .build()

        s3Client.putObject(put, RequestBody.fromInputStream(file.inputStream, file.size))

        return s3Client.utilities().getUrl { it.bucket(bucket).key(fixedKey) }.toExternalForm()
    }

    /**
     * 바이트 배열을 S3에 업로드합니다.
     */
    fun uploadBytes(key: String, bytes: ByteArray, contentType: String) {
        val put = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .build()
        s3Client.putObject(put, RequestBody.fromBytes(bytes))
    }

    /**
     * 지정된 키에 대한 presigned GET URL을 생성합니다.
     */
    fun generatePresignedGetUrl(key: String, expiry: Duration = Duration.ofMinutes(10)): String {
        val get = GetObjectRequest.builder().bucket(bucket).key(key).build()
        val req = GetObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .getObjectRequest(get)
            .build()
        return s3Presigner.presignGetObject(req).url().toString()
    }

    /**
     * 바이트 배열을 업로드하고 presigned GET URL을 반환합니다.
     */
    fun uploadBytesAndGetPresignedGetUrl(
        key: String,
        bytes: ByteArray,
        contentType: String = "image/png",
        expiry: Duration = Duration.ofDays(7)
    ): String {
        uploadBytes(key, bytes, contentType)
        return generatePresignedGetUrl(key, expiry)
    }

    private fun getExtension(filename: String) = filename.substringAfterLast('.', "").lowercase()

    private fun validateFileSize(file: MultipartFile, maxSizeBytes: Long = 5 * 1024 * 1024) {
        if (file.size > maxSizeBytes) {
            throw S3FileException("FILE_TOO_LARGE", "허용된 파일 크기(5MB)를 초과했습니다.")
        }
    }

    private fun validateImageExtension(ext: String) {
        val allowed = setOf("png", "jpg", "jpeg", "webp")
        if (ext !in allowed) throw S3FileException("INVALID_EXTENSION", "허용되지 않은 이미지 확장자입니다: .$ext")
    }

    private fun validateContentType(file: MultipartFile, ext: String) {
        val contentType = file.contentType ?: ""
        val valid = mapOf("jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp")
        if (valid[ext] != contentType) {
            throw S3FileException("CONTENT_TYPE_MISMATCH", "Content-Type이 확장자와 일치하지 않습니다.")
        }
    }
}
