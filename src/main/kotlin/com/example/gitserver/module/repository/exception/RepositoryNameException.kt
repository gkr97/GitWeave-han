package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 저장소 이름이 중복될 때 발생
 */
class DuplicateRepositoryNameException(name: String) : BusinessException(
    code = "REPO_DUPLICATE_NAME",
    message = "이미 존재하는 저장소 이름입니다: $name",
    status = HttpStatus.CONFLICT
)

/**
 * 잘못된 공개 범위 코드 사용 시 발생
 */
class InvalidVisibilityCodeException(code: String?) : BusinessException(
    code = "INVALID_VISIBILITY_CODE",
    message = "유효하지 않은 공개 범위 코드입니다: $code"
)

/**
 * 잘못된 역할 코드 사용 시 발생
 */
class InvalidRoleCodeException(code: String) : BusinessException(
    code = "INVALID_ROLE_CODE",
    message = "유효하지 않은 역할 코드입니다: $code"
)

/**
 * Git 저장소 초기화 실패 시 발생
 */
class GitInitializationFailedException(repoId: Long) : BusinessException(
    code = "GIT_INIT_FAILED",
    message = "Git 저장소 초기화 실패: $repoId",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
)

/**
 * 기본 브랜치에 HEAD 커밋이 없을 때 발생
 */
class HeadCommitNotFoundException(branch: String) : BusinessException(
    code = "HEAD_NOT_FOUND",
    message = "기본 브랜치에 HEAD 커밋이 없습니다: $branch"
)

/**
 * 요청한 사용자가 존재하지 않을 때 발생
 */
class UserNotFoundException(userId: Long)
    : BusinessException(
    code = "USER_NOT_FOUND",
    message = "해당 사용자를 찾을 수 없습니다. userId=$userId"
)

/**
 * 저장소 접근 권한이 없을 때 발생
 */
class RepositoryAccessDeniedException(repoId: Long, userId: Long?)
    : BusinessException(
    code = "REPO_ACCESS_DENIED",
    message = "저장소에 접근할 수 없습니다. repoId=$repoId, userId=$userId",
    status = HttpStatus.FORBIDDEN
)

/**
 * 인증이 필요한 요청(onlyMine 등)
 */
class AuthenticationRequiredException(
    msg: String = "인증이 필요합니다.",
    cause: Throwable? = null
) : BusinessException(
    code = "AUTH_REQUIRED",
    message = msg,
    status = HttpStatus.UNAUTHORIZED,
    cause = cause
)

/**
 * 지원하지 않는 정렬 필드 사용 시 발생
 */
class InvalidSortFieldException(
    field: String,
    cause: Throwable? = null
) : BusinessException(
    code = "INVALID_SORT_FIELD",
    message = "지원하지 않는 정렬 필드입니다: $field",
    status = HttpStatus.BAD_REQUEST,
    cause = cause
)

/**
 * 브랜치 목록(오프셋 페이징) 조회 실패
 */
class BranchQueryFailedException(
    msg: String = "브랜치 목록 조회 중 오류가 발생했습니다.",
    cause: Throwable? = null
) : BusinessException(
    code = "BRANCH_LIST_QUERY_FAILED",
    message = msg,
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)

/**
 * 브랜치 목록(키셋 페이징) 조회 실패
 */
class KeysetQueryFailedException(
    msg: String = "브랜치 키셋 조회 중 오류가 발생했습니다.",
    cause: Throwable? = null
) : BusinessException(
    code = "BRANCH_KEYSET_QUERY_FAILED",
    message = msg,
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)

/**
 * 커서 인코딩 실패
 */
class CursorEncodeFailedException(
    msg: String = "커서 인코딩에 실패했습니다.",
    cause: Throwable? = null
) : BusinessException(
    code = "CURSOR_ENCODE_FAILED",
    message = msg,
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)

/**
 * 저장소 아카이브 생성 실패
 */
class ArchiveCreationFailedException(
    repoId: Long,
    branch: String,
    cause: Throwable? = null
) : BusinessException(
    code = "ARCHIVE_CREATE_FAILED",
    message = "저장소 아카이브 생성에 실패했습니다. repoId=$repoId, branch=$branch",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)

/**
 * 잘못된 페이징 파라미터
 * - page, size, cursor, limit 등의 값이 음수이거나 범위를 벗어나는 경우
 */
class InvalidPagingParameterException(
    msg: String = "유효하지 않은 페이징 파라미터입니다.",
    cause: Throwable? = null
) : BusinessException(
    code = "INVALID_PAGING",
    message = msg,
    status = HttpStatus.BAD_REQUEST,
    cause = cause
)


/** 브랜치가 존재하지 않을 때 */
class BranchNotFoundException(
    repositoryId: Long,
    ref: String,
    cause: Throwable? = null
) : BusinessException(
    code = "BRANCH_NOT_FOUND",
    message = "브랜치를 찾을 수 없습니다. repoId=$repositoryId, ref=$ref",
    status = HttpStatus.NOT_FOUND,
    cause = cause
)

/** 파일트리 조회 실패 */
class FileTreeQueryFailedException(
    repositoryId: Long,
    branch: String,
    commitHash: String,
    cause: Throwable? = null
) : BusinessException(
    code = "FILE_TREE_QUERY_FAILED",
    message = "파일 트리 조회 중 오류가 발생했습니다. repoId=$repositoryId, branch=$branch, commit=$commitHash",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)

/** 파일콘텐츠 조회 실패 */
class FileContentQueryFailedException(
    repositoryId: Long,
    branch: String,
    path: String,
    commitHash: String,
    cause: Throwable? = null
) : BusinessException(
    code = "FILE_CONTENT_QUERY_FAILED",
    message = "파일 콘텐츠 조회 중 오류가 발생했습니다. repoId=$repositoryId, branch=$branch, path=$path, commit=$commitHash",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)
