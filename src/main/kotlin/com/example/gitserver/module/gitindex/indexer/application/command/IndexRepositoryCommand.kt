package com.example.gitserver.module.gitindex.indexer.application.command

import com.example.gitserver.module.gitindex.shared.domain.event.GitEvent
import java.nio.file.Path

sealed interface IndexRepositoryCommand

data class IndexRepositoryHeadCommand(
    val repositoryId: Long,
    val workDir: Path
) : IndexRepositoryCommand

data class IndexRepositoryPushCommand(
    val event: GitEvent,
    val gitDir: Path
) : IndexRepositoryCommand
