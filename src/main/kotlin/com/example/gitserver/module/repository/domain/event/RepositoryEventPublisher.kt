package com.example.gitserver.module.repository.domain.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

private val log = KotlinLogging.logger {}

@Component
class RepositoryEventPublisher(
    private val sqsClient: SqsClient,
    @Value("\${cloud.aws.sqs.queue-url}") private val queueUrl: String
) {
    private val objectMapper = jacksonObjectMapper()

    @Async("virtualThreadTaskExecutor")
    fun publishRepositoryCreatedEvent(event: RepositoryCreatedEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info { "[SQS] 저장소 생성 이벤트 전송 - event=$event" }

            val request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build()

            val response = sqsClient.sendMessage(request)
            log.info { "[SQS] 전송 완료 - messageId=${response.messageId()}" }
        } catch (e: Exception) {
            log.error(e) { "[SQS] 저장소 생성 이벤트 전송 실패 - event=$event" }
        }
    }
}
