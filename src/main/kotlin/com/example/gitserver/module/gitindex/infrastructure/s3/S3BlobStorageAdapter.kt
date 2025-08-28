package com.example.gitserver.module.gitindex.infrastructure.s3

import com.example.gitserver.module.gitindex.domain.port.BlobObjectStorage

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import kotlin.system.measureTimeMillis

@Component
class S3BlobStorageAdapter(
    private val s3Client: S3Client,
    private val presigner: S3Presigner,
    @Value("\${cloud.aws.s3.bucket}") private val bucket: String,
    @Value("\${cloud.aws.s3.endpoint}") private val s3Endpoint: String,
    @Value("\${cloud.aws.s3.public-endpoint:}") private val s3PublicEndpoint: String?
) : BlobObjectStorage {

    private val log = KotlinLogging.logger {}

    private fun keyOf(hash: String) = if (hash.startsWith("blobs/")) hash else "blobs/$hash"

    private fun exists(key: String): Boolean = try {
        s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        true
    } catch (_: Exception) { false }

    override fun putBytes(hash: String, bytes: ByteArray): String {
        val key = keyOf(hash)
        if (!exists(key)) {
            val req = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentLength(bytes.size.toLong()).build()
            s3Client.putObject(req, RequestBody.fromBytes(bytes))
            log.info { "[S3BlobStorage] putBytes ok key=$key size=${bytes.size}" }
        } else {
            log.info { "[S3BlobStorage] putBytes skip exists key=$key" }
        }
        return key
    }

    override fun putStream(hash: String, input: InputStream, contentLength: Long): String {
        val key = keyOf(hash)
        if (!exists(key)) {
            val req = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentLength(contentLength).build()
            s3Client.putObject(req, RequestBody.fromInputStream(input, contentLength))
            log.info { "[S3BlobStorage] putStream ok key=$key size=$contentLength" }
        } else {
            log.info { "[S3BlobStorage] putStream skip exists key=$key" }
        }
        return key
    }

    override fun readAsString(hash: String): String? {
        val key = keyOf(hash)
        return try {
            val resp = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
            val result = resp.readAllBytes().toString(StandardCharsets.UTF_8)
            log.info { "[S3BlobStorage] read ok key=$key" }
            result
        } catch (e: Exception) {
            log.warn(e) { "[S3BlobStorage] read fail key=$key" }
            null
        }
    }

    override fun presignForDownload(
        hash: String,
        downloadFileName: String,
        mimeType: String?,
        ttl: Duration
    ): Pair<String, String> {
        val key = keyOf(hash)
        val getReq = GetObjectRequest.builder()
            .bucket(bucket).key(key)
            .responseContentDisposition("""attachment; filename="$downloadFileName"""")
            .responseContentType(mimeType ?: "application/octet-stream")
            .build()

        val preReq = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(getReq)
            .build()

        lateinit var url: String
        val elapsed = measureTimeMillis {
            url = presigner.presignGetObject(preReq).url().toString()
        }
        val expiresAt = Instant.now().plus(ttl).toString()
        log.info { "[S3BlobStorage] presign ok key=$key ttlMin=${ttl.toMinutes()} elapsedMs=$elapsed" }

        return rewriteIfNeeded(url) to expiresAt
    }

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
            log.info { "[S3BlobStorage] rewrite host ${src.host}:${src.port} -> ${dst.host}:${dst.port}" }
        }
        return replaced.toString()
    }
}
