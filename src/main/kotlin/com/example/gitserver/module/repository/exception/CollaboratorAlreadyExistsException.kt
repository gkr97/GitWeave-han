package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException

class CollaboratorAlreadyExistsException : BusinessException(
    code = "COLLABORATOR_ALREADY_EXISTS",
    message = "이미 collaborator로 등록되어 있습니다."
)