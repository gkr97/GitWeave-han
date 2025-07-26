package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException

class CollaboratorAlreadyAcceptedException : BusinessException(
    code = "COLLABORATOR_ALREADY_ACCEPTED",
    message = "이미 등록된 collaborator입니다."
)