package com.example.gitserver.module.gitindex.shared.domain.port

import com.example.gitserver.module.gitindex.shared.domain.BlobTree

interface TreeRepository {
    fun save(tree: BlobTree)
}