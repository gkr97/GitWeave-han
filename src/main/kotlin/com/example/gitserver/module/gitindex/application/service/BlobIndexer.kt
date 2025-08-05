package com.example.gitserver.module.gitindex.application.service

import com.example.gitserver.module.gitindex.domain.event.GitEvent
import java.nio.file.Path

interface BlobIndexer {
    fun indexRepository(repositoryId: Long, workDir: Path)
    fun indexPush(event: GitEvent, gitDir: Path)
}