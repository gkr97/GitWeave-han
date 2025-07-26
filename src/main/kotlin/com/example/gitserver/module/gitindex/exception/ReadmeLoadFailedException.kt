package com.example.gitserver.module.gitindex.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class ReadmeLoadFailedException(
    key: String,
    cause: Throwable
) : BusinessException(
    code = "README_LOAD_FAILED",
    message = "S3에서 README를 로드하는 데 실패했습니다. key=$key",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)