package com.example.gitserver.module.gitindex.shared.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class ReadmeRenderException(
    cause: Throwable
) : BusinessException(
    code = "README_RENDER_FAILED",
    message = "README 마크다운 렌더링 실패",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)