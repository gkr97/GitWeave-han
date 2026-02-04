package com.example.gitserver.module.gitindex.shared.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class ReadmeNotFoundException(
    repoId: Long,
    commitHash: String
) : BusinessException(
    code = "README_NOT_FOUND",
    message = "README를 찾을 수 없습니다. repositoryId=$repoId, commitHash=$commitHash",
    status = HttpStatus.NOT_FOUND
)