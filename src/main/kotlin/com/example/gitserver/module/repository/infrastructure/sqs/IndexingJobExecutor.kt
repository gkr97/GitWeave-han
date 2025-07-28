package com.example.gitserver.module.repository.infrastructure.sqs

import com.example.gitserver.module.gitindex.domain.service.BlobIndexer
import com.example.gitserver.module.repository.domain.event.RepositoryCreatedEvent
import org.springframework.stereotype.Service

import java.nio.file.Path


@Service
class IndexingJobExecutor(
    private val blobIndexer: BlobIndexer
) {
    /**
     * Repository 생성 이벤트를 처리하여 해당 리포지토리를 인덱싱합니다.
     *
     * @param event RepositoryCreatedEvent - 리포지토리 생성 이벤트
     * @param workDir Path - 작업 디렉토리 경로
     */
    fun indexRepository(event: RepositoryCreatedEvent, workDir: Path) {
        blobIndexer.indexRepository(event.repositoryId, workDir)
    }
}