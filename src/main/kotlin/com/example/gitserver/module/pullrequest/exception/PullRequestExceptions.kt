package com.example.gitserver.module.pullrequest.exception

import com.example.gitserver.common.exception.*

/** PR 모듈 루트 예외(필요 시 타입 계층화) */
open class PullRequestException(message: String, cause: Throwable? = null)
    : DomainException(message, cause)

/* 공통 권한/상태/유효성 */
class PermissionDenied : PullRequestException("권한이 없습니다."), ForbiddenMarker
class NotOpenState : PullRequestException("open 상태에서만 허용됩니다."), ConflictMarker
class InvalidStateTransition(reason: String)
    : PullRequestException(reason), ConflictMarker
class ValidationError(reason: String)
    : PullRequestException(reason), ValidationMarker

/* 브랜치/커밋/저장소 */
class BranchNotFound(ref: String)
    : PullRequestException("브랜치 없음: $ref"), NotFoundMarker
class HeadCommitNotFound(ref: String)
    : PullRequestException("HEAD 커밋을 찾을 수 없습니다: $ref"), NotFoundMarker
class RepositoryMismatch(repoId: Long, prId: Long)
    : PullRequestException("PR이 저장소에 속하지 않습니다. repoId=$repoId, prId=$prId"), ValidationMarker

/* PR 생성/중복 */
class DuplicateOpenPr
    : PullRequestException("동일 source/target의 오픈 PR이 이미 존재합니다."), ConflictMarker
class SameBranchNotAllowed
    : PullRequestException("source와 target 브랜치는 달라야 합니다."), ValidationMarker

/* 리뷰 */
class NotReviewer
    : PullRequestException("리뷰어가 아닙니다."), ForbiddenMarker
class ReviewerAlreadyExists
    : PullRequestException("이미 지정된 리뷰어입니다."), ConflictMarker
class ReviewerNotFound
    : PullRequestException("해당 리뷰어가 없습니다."), NotFoundMarker

/* 코멘트/스레드 */
class CommentNotFound(id: Long)
    : PullRequestException("코멘트 없음: $id"), NotFoundMarker
class ThreadNotFound(id: Long)
    : PullRequestException("스레드 없음: $id"), NotFoundMarker
class InvalidCommentType(type: String)
    : PullRequestException("허용되지 않는 commentType: $type"), ValidationMarker
class InlineCommentNeedsPath
    : PullRequestException("inline/review 코멘트는 filePath가 필요합니다."), ValidationMarker

/* 병합 */
class MergeNotAllowed(reason: String = "모든 리뷰어 승인 필요 혹은 변경 요청이 존재합니다.")
    : PullRequestException(reason), ConflictMarker
class UnsupportedMergeType(type: String)
    : PullRequestException("지원하지 않는 mergeType: $type"), ValidationMarker
