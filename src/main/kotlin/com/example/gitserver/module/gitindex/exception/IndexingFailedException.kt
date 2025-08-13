package com.example.gitserver.module.gitindex.exception

class IndexingFailedException(
    repositoryId: Long,
    commitHash: String,
    cause: Throwable? = null
) : GitIndexingException(
    code = "INDEXING_FAILED",
    message = "인덱싱 실패 - repositoryId=$repositoryId, commitHash=$commitHash",
    cause = cause
)