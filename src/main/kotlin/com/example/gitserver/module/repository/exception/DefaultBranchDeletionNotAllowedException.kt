package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

/** 기본 브랜치 삭제 시도 */
class DefaultBranchDeletionNotAllowedException(
    repositoryId: Long,
    branch: String
) : BusinessException(
    code = "DEFAULT_BRANCH_DELETE_FORBIDDEN",
    message = "기본 브랜치는 삭제할 수 없습니다. repoId=$repositoryId, branch=$branch",
    status = HttpStatus.BAD_REQUEST
)

/** 브랜치 생성 시 기준 브랜치 없음 */
class BaseBranchNotFoundException(
    repositoryId: Long,
    branch: String
) : BusinessException(
    code = "BASE_BRANCH_NOT_FOUND",
    message = "기준 브랜치를 찾을 수 없습니다. repoId=$repositoryId, branch=$branch",
    status = HttpStatus.NOT_FOUND
)

/** Git 연산 실패 */
class GitBranchOperationFailedException(
    operation: String,
    repositoryId: Long,
    branch: String,
    cause: Throwable? = null
) : BusinessException(
    code = "GIT_BRANCH_OPERATION_FAILED",
    message = "Git 브랜치 ${operation} 작업에 실패했습니다. repoId=$repositoryId, branch=$branch, cause=${cause?.message}",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)
