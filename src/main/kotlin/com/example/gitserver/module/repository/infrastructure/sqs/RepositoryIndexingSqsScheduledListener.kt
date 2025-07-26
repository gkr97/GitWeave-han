package com.example.gitserver.module.repository.infrastructure.sqs


import com.example.gitserver.module.gitindex.domain.service.BlobIndexer
import com.example.gitserver.module.repository.domain.event.RepositoryCreatedEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.nio.file.Paths

private val logger = mu.KotlinLogging.logger {}

@Component
class RepositoryIndexingSqsScheduledListener(
    private val sqsClient: SqsClient,
    private val blobIndexer: BlobIndexer,
    @Value("\${git.storage.workdir-path}") private val workdirRootPath: String,
    @Value("\${cloud.aws.sqs.queue-url}") private val queueUrl: String
) {

    private val objectMapper = jacksonObjectMapper()

    /**
     * SQS에서 메시지를 주기적으로 폴링하여 레포지토리 색인 작업을 수행합니다.
     * 5초마다 실행되며, 최대 10개의 메시지를 가져옵니다.
     */
    @Scheduled(fixedDelay = 5000)
    fun pollQueue() {
        val request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(10)
            .waitTimeSeconds(10)
            .build()

        val messages = sqsClient.receiveMessage(request).messages()

        for (message in messages) {
            try {
                val event = objectMapper.readValue<RepositoryCreatedEvent>(message.body())
                val workDir = Paths.get(workdirRootPath, event.ownerId.toString(), event.name)

                blobIndexer.indexRepository(event.repositoryId, workDir)

                sqsClient.deleteMessage {
                    it.queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                }

                logger.info { "색인 완료: ${event.repositoryId} (${event.ownerId}/${event.name})" }
            } catch (e: Exception) {
                logger.error(e) { "색인 작업 실패: ${message.body()}" }
                // TODO: DLQ 처리 또는 로그 적재
            }
        }
    }
}
