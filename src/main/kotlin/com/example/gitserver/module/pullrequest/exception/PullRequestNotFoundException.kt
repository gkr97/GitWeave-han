package com.example.gitserver.module.pullrequest.exception

import com.example.gitserver.common.exception.DomainException
import com.example.gitserver.common.exception.NotFoundMarker

/**
 * Pull Request를 찾을 수 없을 때 사용하는 예외
 */
class PullRequestNotFoundException(
    prId: Long,
    override val message: String = "Pull Request를 찾을 수 없습니다: $prId"
) : PullRequestException(message), NotFoundMarker
