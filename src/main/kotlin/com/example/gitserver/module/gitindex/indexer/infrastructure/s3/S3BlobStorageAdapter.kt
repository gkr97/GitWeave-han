package com.example.gitserver.module.gitindex.indexer.infrastructure.s3

import com.example.gitserver.common.resilience.ResilienceUtils
import com.example.gitserver.module.gitindex.shared.domain.port.BlobObjectStorage
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import kotlin.system.measureTimeMillis

@Component
@Profile("gitindexer")
class S3BlobStorageAdapter(
    private val s3Client: S3Client,
    private val presigner: S3Presigner,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    @Value("\${cloud.aws.s3.bucket}") private val bucket: String,
    @Value("\${cloud.aws.s3.endpoint}") private val s3Endpoint: String,
    @Value("\${cloud.aws.s3.public-endpoint:}") private val s3PublicEndpoint: String?
) : BlobObjectStorage {

    private val log = KotlinLogging.logger {}

    private fun keyOf(hash: String) = if (hash.startsWith("blobs/")) hash else "blobs/$hash"

    private fun exists(key: String): Boolean = try {
        s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        true
    } catch (_: NoSuchKeyException) {
        false
    } catch (_: Exception) {
        false
    }

    /**
     * 바이트 배열을 S3에 저장합니다.
     * Circuit Breaker와 Retry 적용
     */
    override fun putBytes(hash: String, bytes: ByteArray): String {
        val key = keyOf(hash)
        return ResilienceUtils.executeWithResilience(
            circuitBreakerName = "s3",
            retryName = "s3",
            circuitBreakerRegistry = circuitBreakerRegistry,
            retryRegistry = retryRegistry
        ) {
            if (!exists(key)) {
                val req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentLength(bytes.size.toLong())
                    .build()
                s3Client.putObject(req, RequestBody.fromBytes(bytes))
                log.debug { "[S3BlobStorage] putBytes ok key=$key size=${bytes.size}" }
            } else {
                log.debug { "[S3BlobStorage] putBytes skip exists key=$key" }
            }
            key
        }
    }

    /**
     * 스트림을 S3에 저장합니다.
     */
    override fun putStream(hash: String, input: InputStream, contentLength: Long): String {
        val key = keyOf(hash)
        if (!exists(key)) {
            val req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(contentLength)
                .build()
            s3Client.putObject(req, RequestBody.fromInputStream(input, contentLength))
            log.debug { "[S3BlobStorage] putStream ok key=$key size=$contentLength" }
        } else {
            log.debug { "[S3BlobStorage] putStream skip exists key=$key" }
        }
        return key
    }

    /**
     * S3에서 바이트 배열을 읽어옵니다.
     */
    override fun readAsString(hash: String): String? {
        val key = keyOf(hash)
        return try {
            s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()).use { resp ->
                val result = resp.readAllBytes().toString(StandardCharsets.UTF_8)
                log.debug { "[S3BlobStorage] read ok key=$key" }
                result
            }
        } catch (e: Exception) {
            log.warn(e) { "[S3BlobStorage] read fail key=$key" }
            null
        }
    }

    /**
     * S3에서 다운로드용 사전 서명된 URL을 생성합니다.
     * Circuit Breaker와 Retry 적용
     */
    override fun presignForDownload(
        hash: String,
        downloadFileName: String,
        mimeType: String?,
        ttl: Duration
    ): Pair<String, String> {
        val key = keyOf(hash)
        return ResilienceUtils.executeWithResilience(
            circuitBreakerName = "s3",
            retryName = "s3",
            circuitBreakerRegistry = circuitBreakerRegistry,
            retryRegistry = retryRegistry
        ) {
            val getReq = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition(buildContentDisposition(downloadFileName))
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

            rewriteIfNeeded(url) to expiresAt
        }
    }
    /**
     * 퍼블릭 엔드포인트가 설정된 경우, 사전 서명된 URL을 해당 엔드포인트로 재작성합니다.
     */
    private fun rewriteIfNeeded(url: String): String {
        val pub = s3PublicEndpoint?.takeIf { it.isNotBlank() } ?: return url
        val src = URI.create(s3Endpoint)
        val dst = URI.create(pub)
        val u = URI.create(url)

        val replaced = URI(
            dst.scheme ?: u.scheme,
            u.userInfo,
            dst.host,
            if (dst.port != -1) dst.port else u.port,
            u.path,
            u.query,
            u.fragment
        )

        if (src.host != dst.host || (dst.scheme != null && dst.scheme != u.scheme)) {
            log.info { "[S3BlobStorage] rewrite ${u.scheme}://${src.host}:${src.port} -> ${replaced.scheme}://${dst.host}:${replaced.port}" }
        }
        return replaced.toString()
    }

    private fun buildContentDisposition(filename: String): String {
        val safe = filename.replace("\"", "'")
        val encoded = rfc5987Encode(filename)
        return """attachment; filename="$safe"; filename*=UTF-8''$encoded"""
    }

    private fun rfc5987Encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%7E", "~")
}
