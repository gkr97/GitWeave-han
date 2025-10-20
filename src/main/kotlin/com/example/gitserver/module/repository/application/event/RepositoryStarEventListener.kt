package com.example.gitserver.module.repository.application.event

import com.example.gitserver.module.repository.domain.event.RepositoryStarred
import com.example.gitserver.module.repository.domain.event.RepositoryUnstarred
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryStatsRepository
import com.example.gitserver.module.repository.infrastructure.redis.RedisCountService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.event.TransactionPhase

@Component
class RepositoryStarEventListener(
    private val redisCountService: RedisCountService,
    private val repositoryStatsRepository: RepositoryStatsRepository
) {
    private val log = LoggerFactory.getLogger(RepositoryStarEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleStarred(evt: RepositoryStarred) {
        try {
            redisCountService.incrementRepoStars(evt.repositoryId)

            repositoryStatsRepository.incrementStars(evt.repositoryId)
        } catch (ex: Exception) {
            log.error("failed to apply star updates for repo=${evt.repositoryId}", ex)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUnstarred(evt: RepositoryUnstarred) {
        try {
            redisCountService.decrementRepoStars(evt.repositoryId)
            repositoryStatsRepository.decrementStars(evt.repositoryId)
        } catch (ex: Exception) {
            log.error("failed to apply unstar updates for repo=${evt.repositoryId}", ex)
        }
    }
}
