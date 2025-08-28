package com.example.gitserver.module.gitindex.domain.port

import com.example.gitserver.module.gitindex.domain.dto.FileMeta

interface BlobQueryRepository {
    fun countBlobsByExtension(repoId: Long): Map<String, Int>
    fun getBlobMeta(repoId: Long, fileHash: String): FileMeta?
}