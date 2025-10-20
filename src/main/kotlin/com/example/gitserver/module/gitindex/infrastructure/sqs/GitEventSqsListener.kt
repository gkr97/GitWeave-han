package com.example.gitserver.module.gitindex.infrastructure.sqs

import com.example.gitserver.module.gitindex.domain.event.GitEvent
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import org.springframework.beans.factory.annotation.Value
import java.util.concurrent.Semaphore
import java.nio.file.Paths
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import java.nio.file.Files

/**
 * SQS에서 Git 이벤트 메시지를 폴링하고 처리하는 리스너입니다.
 */
@Component
class GitEventSqsListener(
    private val sqsClient: SqsClient,
    private val indexingJobExecutor: IndexingJobExecutor,
    @Value("\${cloud.aws.sqs.queue-url}") private val queueUrl: String,
    @Value("\${git.storage.workdir-path}") private val workdirRootPath: String,
    @Value("\${git.storage.bare-path}") private val bareRootPath: String
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
                        val event = objectMapper.readValue<GitEvent>(message.body())

                        when (event.eventType) {
                            "REPO_CREATED" -> {
                                val workDir = Paths.get(workdirRootPath, event.ownerId.toString(), event.name)
                                indexingJobExecutor.indexRepository(event, workDir)
                                try {
                                    Files.walk(workDir)
                                        .sorted(Comparator.reverseOrder())
                                        .forEach { Files.deleteIfExists(it) }
                                    log.info { "워킹 디렉토리 삭제 완료: $workDir" }
                                } catch (e: Exception) {
                                    log.warn(e) { "워킹 디렉토리 삭제 실패: $workDir" }
                                }
                            }
                            "PUSH" -> {
                                val bareRepoDir = Paths.get(bareRootPath, event.ownerId.toString(), "${event.name}.git")
                                log.info { "PUSH 이벤트 처리: repo=${event.name}, branch=${event.branch}, old=${event.oldrev}, new=${event.newrev}" }
                                indexingJobExecutor.indexRepository(event, bareRepoDir)
                            }
                            else -> {
                                log.warn { "알 수 없는 이벤트 타입: ${event.eventType}" }
                            }
                        }

                        sqsClient.deleteMessage {
                            it.queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                        }

                        log.info { "이벤트 처리 완료 (Virtual Thread): type=${event.eventType}, repoId=${event.repositoryId}" }
                    } catch (e: Exception) {
                        log.error(e) { "이벤트 처리 실패: ${message.body()}" }
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
