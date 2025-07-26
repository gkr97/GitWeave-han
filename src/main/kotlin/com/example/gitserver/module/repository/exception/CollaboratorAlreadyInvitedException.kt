package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException

class CollaboratorAlreadyInvitedException : BusinessException(
    code = "COLLABORATOR_ALREADY_INVITED",
    message = "이미 초대된 사용자입니다."
)