package com.example.gitserver.module.gitindex.infrastructure.sqs

import com.example.gitserver.module.gitindex.application.command.IndexRepositoryHeadCommand
import com.example.gitserver.module.gitindex.application.command.IndexRepositoryPushCommand
import com.example.gitserver.module.gitindex.application.command.handler.IndexRepositoryCommandHandler
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class IndexingJobExecutor(
    private val handler: IndexRepositoryCommandHandler
) {

    /**
     * Git 이벤트에 따라 레포지토리 인덱싱 작업을 실행합니다.
     * @param event Git 이벤트 정보
     * @param gitDir Git 작업 디렉토리 경로
     */
    fun indexRepository(event: GitEvent, gitDir: Path) {
        when (event.eventType) {
            "REPO_CREATED" ->
                handler.handle(IndexRepositoryHeadCommand(event.repositoryId, gitDir))
            "PUSH" ->
                handler.handle(IndexRepositoryPushCommand(event, gitDir))
            else -> {
                // TODO 필요 시 추가 이벤트 처리
            }
        }
    }
}
