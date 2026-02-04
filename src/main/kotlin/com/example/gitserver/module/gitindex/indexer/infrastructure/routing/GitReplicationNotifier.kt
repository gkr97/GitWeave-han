package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

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
class GitReplicationNotifier(
    private val objectMapper: ObjectMapper,
    @Value("\${git.replication.alert-webhook:}") private val webhookUrl: String
) {
    private val log = KotlinLogging.logger {}
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    fun notifyFailure(task: GitReplicationTaskEntity, error: String?) {
        if (webhookUrl.isBlank()) return
        val payload = mapOf(
            "event" to "git_replication_failed",
            "taskId" to task.id,
            "repoId" to task.repoId,
            "sourceNodeId" to task.sourceNodeId,
            "targetNodeId" to task.targetNodeId,
            "attempt" to task.attempt,
            "error" to error
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
            log.warn(e) { "[ReplicationNotify] failed" }
        }
    }

    fun notifyDlqReprocessFailure(dlqId: Long, error: String?) {
        if (webhookUrl.isBlank()) return
        val payload = mapOf(
            "event" to "git_replication_dlq_reprocess_failed",
            "dlqId" to dlqId,
            "error" to error
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
            log.warn(e) { "[ReplicationNotify] dlq reprocess failed" }
        }
    }
}
