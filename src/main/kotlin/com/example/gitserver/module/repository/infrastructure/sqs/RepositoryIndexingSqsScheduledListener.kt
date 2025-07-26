package com.example.gitserver.module.repository.infrastructure.sqs

import com.example.gitserver.module.repository.domain.event.RepositoryCreatedEvent
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import org.springframework.beans.factory.annotation.Value
import java.util.concurrent.Semaphore
import java.nio.file.Paths
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging

@Component
class RepositoryIndexingSqsScheduledListener(
    private val sqsClient: SqsClient,
    private val indexingJobExecutor: IndexingJobExecutor,
    @Value("\${cloud.aws.sqs.queue-url}") private val queueUrl: String,
    @Value("\${git.storage.workdir-path}") private val workdirRootPath: String
) {
    private val log = KotlinLogging.logger {}
    private val semaphore = Semaphore(10)
    private val objectMapper = jacksonObjectMapper()

    @Scheduled(fixedDelay = 5000)
    fun pollQueue() {
        val messages = sqsClient.receiveMessage {
            it.queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10)
                .visibilityTimeout(300)
        }.messages()

        messages.forEach { message ->
            if (semaphore.tryAcquire()) {
                Thread.startVirtualThread {
                    try {
                        val event = objectMapper.readValue<RepositoryCreatedEvent>(message.body())
                        val workDir = Paths.get(workdirRootPath, event.ownerId.toString(), event.name)

                        indexingJobExecutor.indexRepository(event, workDir)

                        sqsClient.deleteMessage {
                            it.queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                        }

                        log.info { "색인 완료 (Virtual Thread): ${event.repositoryId}" }
                    } catch (e: Exception) {
                        log.error(e) { "색인 실패: ${message.body()}" }
                    } finally {
                        semaphore.release()
                    }
                }
            } else {
                log.warn { "병렬 한도 초과. 메시지 건너뜀: ${message.messageId()}" }
            }
        }
    }
}
