package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException

class InviteeAlreadyAcceptedException : BusinessException(
    code = "COLLABORATOR_ALREADY_ACCEPTED",
    message = "이미 수락된 collaborator입니다."
)