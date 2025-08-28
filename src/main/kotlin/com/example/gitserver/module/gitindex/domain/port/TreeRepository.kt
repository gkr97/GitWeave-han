package com.example.gitserver.module.gitindex.domain.port

import com.example.gitserver.module.gitindex.domain.BlobTree

interface TreeRepository {
    fun save(tree: BlobTree)
}