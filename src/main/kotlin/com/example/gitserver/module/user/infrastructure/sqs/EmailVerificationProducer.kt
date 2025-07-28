package com.example.gitserver.module.user.infrastructure.sqs

import com.example.gitserver.module.user.interfaces.rest.dto.EmailVerificationMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Service
class EmailVerificationProducer(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    @Value("\${mail.sqs.queue-url}") private val queueUrl: String
) {
    /**
     * 이메일 인증 메시지를 SQS 큐에 전송합니다.
     *
     * @param message 이메일 인증 메시지 객체
     */
    fun sendVerificationMailMessage(message: EmailVerificationMessage) {
        val req = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(objectMapper.writeValueAsString(message))
            .build()
        sqsClient.sendMessage(req)
    }
}

