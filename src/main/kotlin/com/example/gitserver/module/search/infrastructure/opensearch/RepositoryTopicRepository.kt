package com.example.gitserver.module.search.infrastructure.opensearch

interface RepositoryTopicRepository {
    fun findTopicsByRepositoryId(repositoryId: Long): List<String>
}