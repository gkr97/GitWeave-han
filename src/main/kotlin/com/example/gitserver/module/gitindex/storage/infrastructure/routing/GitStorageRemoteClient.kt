package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.storage.interfaces.GitStorageInitRequest
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.springframework.context.annotation.Profile

@Component
@Profile("core")
class GitStorageRemoteClient(
    private val objectMapper: ObjectMapper,
    @Value("\${git.routing.admin-key:}") private val adminKey: String,
    @Value("\${git.routing.scheme:http}") private val scheme: String,
    @Value("\${git.routing.storage-timeout-ms:10000}") private val timeoutMs: Long
) {
    private val log = KotlinLogging.logger {}
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    fun initRepository(host: String, request: GitStorageInitRequest): Boolean {
        val url = "$scheme://$host/internal/git/storage/init"
        return try {
            val body = objectMapper.writeValueAsBytes(request)
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            if (adminKey.isNotBlank()) {
                builder.header("X-Git-Admin-Key", adminKey)
            }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding())
            response.statusCode() in 200..299
        } catch (e: Exception) {
            log.warn(e) { "[GitStorageRemote] init failed host=$host" }
            false
        }
    }
}
