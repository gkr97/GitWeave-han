package com.example.gitserver.module.user.infrastructure.s3

import com.example.gitserver.module.user.exception.S3FileException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class S3Uploader(
    private val s3Client: S3Client,

    @Value("\${cloud.aws.s3.bucket}")
    private val bucket: String,

    @Value("\${cloud.aws.s3.endpoint}")
    private val endpointUrl: String
) {
    /**
     * S3에 파일을 업로드하고 URL을 반환합니다.
     * @param file 업로드할 파일
     * @param path S3 버킷 내의 경로
     * @return 업로드된 파일의 URL
     * @throws S3FileException 파일 유효성 검사 실패 시 예외 발생
     */
    fun upload(file: MultipartFile, path: String): String {
        val originalFilename = file.originalFilename
            ?: throw S3FileException("INVALID_FILENAME", "파일명이 없습니다.")

        val extension = getExtension(originalFilename)
        validateImageExtension(extension)
        validateFileSize(file)
        validateFilename(originalFilename)
        validateContentType(file, extension)

        val normalizedPath = if (path.endsWith("/")) path else "$path/"
        val key = "$normalizedPath${UUID.randomUUID()}.$extension"
        val encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.toString())

        val putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.contentType)
            .acl(ObjectCannedACL.PUBLIC_READ) // 추후 presigned URL로 대체 할예정
            .build()

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.inputStream, file.size))

        return "$endpointUrl/$bucket/$encodedKey"
    }

    /**
     * 파일 이름에서 확장자를 추출합니다.
     * @param filename 파일 이름
     * @return 파일 확장자 (소문자)
     */
    private fun getExtension(filename: String): String {
        return filename.substringAfterLast('.', "").lowercase()
    }

    /**
     * 파일 크기를 검증합니다.
     * @param file 업로드할 파일
     * @param maxSizeBytes 최대 허용 파일 크기 (기본값: 5MB)
     * @throws S3FileException 파일 크기가 허용 범위를 초과하는 경우 예외 발생
     */
    private fun validateFileSize(file: MultipartFile, maxSizeBytes: Long = 5 * 1024 * 1024) {
        if (file.size > maxSizeBytes) {
            throw S3FileException("FILE_TOO_LARGE", "허용된 파일 크기(5MB)를 초과했습니다.")
        }
    }

    /**
     * 파일 이름을 검증합니다.
     * @param filename 검증할 파일 이름
     * @throws S3FileException 파일 이름이 유효하지 않은 경우 예외 발생
     */
    private fun validateFilename(filename: String) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw S3FileException("INVALID_FILENAME", "올바르지 않은 파일명입니다.")
        }
    }

    /**
     * 이미지 파일의 확장자를 검증합니다.
     * @param ext 파일 확장자
     * @throws S3FileException 허용되지 않은 확장자인 경우 예외 발생
     */
    private fun validateImageExtension(ext: String) {
        val allowed = setOf("png", "jpg", "jpeg", "webp")
        if (ext !in allowed) {
            throw S3FileException("INVALID_EXTENSION", "허용되지 않은 이미지 확장자입니다: .$ext")
        }
    }

    /**
     * 파일의 Content-Type이 확장자와 일치하는지 검증합니다.
     * @param file 업로드할 파일
     * @param ext 파일 확장자
     * @throws S3FileException Content-Type이 확장자와 일치하지 않는 경우 예외 발생
     */
    private fun validateContentType(file: MultipartFile, ext: String) {
        val contentType = file.contentType ?: ""
        val validMapping = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "webp" to "image/webp"
        )
        if (validMapping[ext] != contentType) {
            throw S3FileException("CONTENT_TYPE_MISMATCH", "Content-Type이 확장자와 일치하지 않습니다.")
        }
    }
}
