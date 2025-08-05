package com.example.gitserver.module.user.infrastructure.sqs

import com.example.gitserver.common.util.MailUtils
import com.example.gitserver.module.user.interfaces.dto.EmailVerificationMessage
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

    /**
     * SQS에서 이메일 인증 메시지를 주기적으로 폴링하여 처리합니다.
     * - 메시지를 받아와서 JSON으로 파싱합니다.
     * - 이메일을 발송하고 성공 시 메시지를 삭제합니다.
     * - 실패 시 에러 로그를 기록합니다.
     */
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
