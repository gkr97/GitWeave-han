package com.example.gitserver.module.gitindex.exception

class S3UploadException(path: String, hash: String, cause: Throwable?) : GitIndexingException(
    code = "S3_UPLOAD_FAILED",
    message = "S3 업로드 실패 - path=$path, hash=$hash",
    cause = cause
)