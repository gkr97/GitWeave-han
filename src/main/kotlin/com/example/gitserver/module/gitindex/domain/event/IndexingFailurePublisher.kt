package com.example.gitserver.module.gitindex.domain.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import java.time.Instant

@Component
class IndexingFailurePublisher(
    private val sqs: SqsClient,
    @Value("\${cloud.aws.sqs.indexing-failure-queue-url}")
    private val failureQueueUrl: String
) {

    private val mapper = jacksonObjectMapper()

    data class FailurePayload(
        val repositoryId: Long,
        val commitHash: String,
        val path: String,
        val objectId: String,
        val reason: String,
        val error: String?,
        val timestamp: Long = Instant.now().toEpochMilli()
    )

    fun publishFileFailure(
        repositoryId: Long,
        commitHash: String,
        path: String,
        objectId: String,
        reason: String,
        throwable: Throwable?
    ) {
        val payload = FailurePayload(
            repositoryId = repositoryId,
            commitHash = commitHash,
            path = path,
            objectId = objectId,
            reason = reason,
            error = throwable?.let { "${it::class.java.name}: ${it.message}" }
        )
        val body = mapper.writeValueAsString(payload)
        sqs.sendMessage { it.queueUrl(failureQueueUrl).messageBody(body) }
    }
}
