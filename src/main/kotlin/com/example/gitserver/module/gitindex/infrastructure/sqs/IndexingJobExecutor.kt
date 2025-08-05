package com.example.gitserver.module.gitindex.infrastructure.sqs

import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.application.service.BlobIndexer
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class IndexingJobExecutor(
    private val blobIndexer: BlobIndexer
) {

    /**
     * Git 이벤트에 따라 레포지토리 인덱싱 작업을 실행합니다.
     * @param event Git 이벤트 정보
     * @param gitDir Git 작업 디렉토리 경로
     */
    fun indexRepository(event: GitEvent, gitDir: Path) {
        when (event.eventType) {
            "REPO_CREATED" -> {
                blobIndexer.indexRepository(event.repositoryId, gitDir)
            }
            "PUSH" -> {
                blobIndexer.indexPush(event, gitDir)
            }
            else -> {
                // TODO 필요 시 추가 이벤트 처리
            }
        }
    }
}
