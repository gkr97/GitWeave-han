package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryStarRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryStatsRepository
import com.example.gitserver.module.user.domain.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.example.gitserver.module.repository.infrastructure.redis.RedisCountService
import java.time.Duration

@Service
class RepositoryStarQueryService(
    private val redisCountService: RedisCountService,
    private val starRepository: RepositoryStarRepository,
    private val statsRepository: RepositoryStatsRepository? = null
) {

    private val log = LoggerFactory.getLogger(RepositoryStarQueryService::class.java)

    companion object {
        private const val CACHE_TTL_SECONDS: Long = 60 * 5
    }

    @Transactional(readOnly = true)
    fun hasStar(userId: Long, repositoryId: Long): Boolean {
        return try {
            starRepository.existsByUserIdAndRepositoryId(userId, repositoryId)
        } catch (ex: Exception) {
            log.error("hasStar check failed user=$userId repo=$repositoryId", ex)
            false
        }
    }

    @Transactional(readOnly = true)
    fun countStars(repositoryId: Long): Int {
        try {
            val cached = redisCountService.getRepoStars(repositoryId)
            if (cached >= 0) return cached

            val dbCount: Int = try {
                if (statsRepository != null) {
                    statsRepository.findById(repositoryId)
                        .map { it.stars }
                        .orElseGet {
                            starRepository.countByRepositoryId(repositoryId)
                        }
                } else {
                    starRepository.countByRepositoryId(repositoryId)
                }
            } catch (ex: Exception) {
                log.warn("DB count for stars failed repo=$repositoryId, fallback to 0", ex)
                0
            }

            try {
                redisCountService.setRepoStars(repositoryId, dbCount, Duration.ofSeconds(CACHE_TTL_SECONDS))
            } catch (ex: Exception) {
                log.warn("Failed to set redis cache for repo=$repositoryId", ex)
            }

            return dbCount
        } catch (ex: Exception) {
            log.error("countStars failed for repo=$repositoryId", ex)
            return 0
        }
    }


    @Transactional(readOnly = true)
    fun listStargazers(repositoryId: Long): List<User> {
        return try {
            val stars = starRepository.findAllByRepositoryId(repositoryId)
            stars.map { it.user }
        } catch (ex: Exception) {
            log.error("listStargazers failed repo=$repositoryId", ex)
            emptyList()
        }
    }
}
