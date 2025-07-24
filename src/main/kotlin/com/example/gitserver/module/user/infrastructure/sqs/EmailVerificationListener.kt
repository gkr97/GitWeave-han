package com.example.gitserver.module.user.infrastructure.sqs

import com.example.gitserver.common.util.MailUtils
import com.example.gitserver.module.user.interfaces.rest.dto.EmailVerificationMessage
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.SqsClient

@Service
class EmailVerificationListener(
    private val mailUtils: MailUtils,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    @Value("\${mail.sqs.queue-url}") private val queueUrl: String
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelay = 2000)
    fun pollSqs() {
        val response = sqsClient.receiveMessage { it.queueUrl(queueUrl).maxNumberOfMessages(10) }
        for (message in response.messages()) {
            try {
                val payload = objectMapper.readValue(message.body(), EmailVerificationMessage::class.java)
                mailUtils.sendEmail(
                    to = payload.email,
                    subject = payload.subject,
                    body = payload.body
                )
                log.info { "이메일 인증 메일 발송 완료(Consumer): ${payload.email}, token=${payload.token}" }
                sqsClient.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receiptHandle()) }
            } catch (e: Exception) {
                log.error(e) { "이메일 인증 메일 발송 실패: ${message.body()}" }
            }
        }
    }
}
