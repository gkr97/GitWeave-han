package com.example.gitserver.module.search.infrastructure.opensearch

import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.stereotype.Repository

private val log = KotlinLogging.logger {}

@Repository
class RepositoryTopicRepositoryImpl(
    private val repositoryRepository: RepositoryRepository
) : RepositoryTopicRepository {

    override fun findTopicsByRepositoryId(repositoryId: Long): List<String> {
        val repo = repositoryRepository.findById(repositoryId).orElse(null)
        val raw = repo?.topics ?: return emptyList()

        return raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .distinct()
            .toList()
    }
}
