package com.example.gitserver.module.gitindex.storage.infrastructure.event

import com.example.gitserver.module.gitindex.shared.domain.event.GitEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

private val log = KotlinLogging.logger {}

@Component
@Profile("gitstorage")
class GitEventPublisher(
    private val sqsClient: SqsClient,
    @Value("\${cloud.aws.sqs.queue-url}") private val queueUrl: String
) {
    private val objectMapper = jacksonObjectMapper()

    @Async("virtualThreadTaskExecutor")
    fun publish(event: GitEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info { "[SQS] Git 이벤트 전송 - event=$event" }

            val request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build()

            val response = sqsClient.sendMessage(request)
            log.info { "[SQS] 전송 완료 - messageId=${response.messageId()}" }
        } catch (e: Exception) {
            log.error(e) { "[SQS] 이벤트 전송 실패 - event=$event" }
        }
    }
}
