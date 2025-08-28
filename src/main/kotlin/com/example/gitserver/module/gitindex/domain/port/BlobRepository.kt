package com.example.gitserver.module.gitindex.domain.port

import com.example.gitserver.module.gitindex.domain.Blob

interface BlobRepository {
    fun save(blob: Blob)
}