package com.example.gitserver.module.gitindex.application.service

import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit

interface GitIndexWriter {
    fun saveCommit(commit: Commit)
    fun saveBlob(blob: Blob)
    fun saveTree(tree: BlobTree)
    fun saveBlobAndTree(blob: Blob, tree: BlobTree)
}