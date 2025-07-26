package com.example.gitserver.module.gitindex.domain.service

import java.nio.file.Path

interface BlobIndexer {
    fun indexRepository(repositoryId: Long, workDir: Path)
}