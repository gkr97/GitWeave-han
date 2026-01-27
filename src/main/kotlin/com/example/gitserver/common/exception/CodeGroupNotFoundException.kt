package com.example.gitserver.common.exception

/**
 * 공통 코드 그룹을 찾을 수 없을 때 사용하는 예외
 */
class CodeGroupNotFoundException(
    code: String,
    override val message: String = "코드 그룹을 찾을 수 없습니다: $code"
) : BusinessException(
    code = "CODE_GROUP_NOT_FOUND",
    message = message,
    status = org.springframework.http.HttpStatus.NOT_FOUND
), NotFoundMarker
