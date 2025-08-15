package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class GitRepositoryAlreadyExistsException(
    ownerId: Long,
    name: String
) : BusinessException(
    code = "GIT_REPO_ALREADY_EXISTS",
    message = "이미 존재하는 저장소 디렉터리입니다. ownerId=$ownerId, name=$name",
    status = HttpStatus.CONFLICT
)

class OwnerNameMissingException(
    userId: Long
) : BusinessException(
    code = "OWNER_NAME_MISSING",
    message = "소유자 이름이 없습니다. userId=$userId",
    status = HttpStatus.BAD_REQUEST
)

class BranchAlreadyExistsException(
    repositoryId: Long,
    ref: String
) : BusinessException(
    code = "BRANCH_ALREADY_EXISTS",
    message = "이미 존재하는 브랜치입니다. repoId=$repositoryId, ref=$ref",
    status = HttpStatus.CONFLICT
)

class GitInitialCommitOrPushFailedException(
    repoId: Long,
    detail: String,
    cause: Throwable? = null
) : BusinessException(
    code = "GIT_INITIAL_COMMIT_OR_PUSH_FAILED",
    message = "Git 초기 커밋/푸시 작업 실패: repoId=$repoId, detail=$detail",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)

class RepositoryRenameFailedException(
    ownerId: Long,
    oldName: String,
    newName: String,
    cause: Throwable? = null
) : BusinessException(
    code = "REPO_RENAME_FAILED",
    message = "저장소 디렉터리 이름 변경 실패: ownerId=$ownerId, $oldName -> $newName",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)

class RepositoryDirectoryNotFoundException(
    ownerId: Long,
    name: String
) : BusinessException(
    code = "REPO_DIR_NOT_FOUND",
    message = "저장소 디렉터리를 찾을 수 없습니다. ownerId=$ownerId, name=$name",
    status = HttpStatus.NOT_FOUND
)

class RepositoryDirectoryAlreadyExistsException(
    ownerId: Long,
    name: String
) : BusinessException(
    code = "REPO_DIR_ALREADY_EXISTS",
    message = "이미 존재하는 대상 디렉터리입니다. ownerId=$ownerId, name=$name",
    status = HttpStatus.CONFLICT
)
