package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class DuplicateRepositoryNameException(name: String) : BusinessException(
    code = "REPO_DUPLICATE_NAME",
    message = "이미 존재하는 저장소 이름입니다: $name",
    status = HttpStatus.CONFLICT
)

class InvalidVisibilityCodeException(code: String?) : BusinessException(
    code = "INVALID_VISIBILITY_CODE",
    message = "유효하지 않은 공개 범위 코드입니다: $code"
)

class InvalidRoleCodeException(code: String) : BusinessException(
    code = "INVALID_ROLE_CODE",
    message = "유효하지 않은 역할 코드입니다: $code"
)

class GitInitializationFailedException(repoId: Long) : BusinessException(
    code = "GIT_INIT_FAILED",
    message = "Git 저장소 초기화 실패: $repoId",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
)

class HeadCommitNotFoundException(branch: String) : BusinessException(
    code = "HEAD_NOT_FOUND",
    message = "기본 브랜치에 HEAD 커밋이 없습니다: $branch"
)

class UserNotFoundException(userId: Long) : BusinessException(
    code = "USER_NOT_FOUND",
    message = "해당 사용자를 찾을 수 없습니다. userId=$userId"
)

class RepositoryAccessDeniedException(repoId: Long, userId: Long?) : BusinessException(
    code = "REPO_ACCESS_DENIED",
    message = "저장소에 접근할 수 없습니다. repoId=$repoId, userId=$userId",
    status = HttpStatus.FORBIDDEN
)