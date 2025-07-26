package com.example.gitserver.module.repository.infrastructure.sqs

import com.example.gitserver.module.gitindex.domain.service.BlobIndexer
import com.example.gitserver.module.repository.domain.event.RepositoryCreatedEvent
import org.springframework.stereotype.Service

import java.nio.file.Path


@Service
class IndexingJobExecutor(
    private val blobIndexer: BlobIndexer
) {
    fun indexRepository(event: RepositoryCreatedEvent, workDir: Path) {
        blobIndexer.indexRepository(event.repositoryId, workDir)
    }
}