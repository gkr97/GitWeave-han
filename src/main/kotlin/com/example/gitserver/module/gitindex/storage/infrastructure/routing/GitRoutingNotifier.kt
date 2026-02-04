package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class GitRoutingNotifier(
    private val objectMapper: ObjectMapper,
    @Value("\${git.routing.failover-alert-webhook:}") private val webhookUrl: String
) {
    private val log = KotlinLogging.logger {}
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    fun notifyFailover(repoId: Long, oldPrimary: String, newPrimary: String) {
        if (webhookUrl.isBlank()) return
        val payload = mapOf(
            "event" to "git_failover",
            "repoId" to repoId,
            "oldPrimary" to oldPrimary,
            "newPrimary" to newPrimary
        )

        runCatching {
            val body = objectMapper.writeValueAsString(payload)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            client.send(request, HttpResponse.BodyHandlers.discarding())
        }.onFailure { e ->
            log.warn(e) { "[RoutingNotify] failed" }
        }
    }
}
