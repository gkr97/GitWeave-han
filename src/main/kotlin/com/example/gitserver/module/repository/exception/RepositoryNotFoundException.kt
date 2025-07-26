package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class RepositoryNotFoundException(repoId: Long) : BusinessException(
    code = "REPO_NOT_FOUND",
    message = "레포를 찾을 수 없습니다. id=$repoId",
    status = HttpStatus.NOT_FOUND
)