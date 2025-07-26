package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class CollaboratorNotFoundException(repoId: Long, userId: Long) : BusinessException(
    code = "COLLABORATOR_NOT_FOUND",
    message = "초대를 찾을 수 없습니다. repoId=$repoId, userId=$userId",
    status = HttpStatus.NOT_FOUND
)