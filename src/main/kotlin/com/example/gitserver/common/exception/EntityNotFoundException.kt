package com.example.gitserver.common.exception

/**
 * 엔티티를 찾을 수 없을 때 사용하는 일관된 예외
 * IllegalArgumentException, NoSuchElementException 대신 사용
 */
class EntityNotFoundException(
    entityType: String,
    identifier: Any,
    override val message: String = "$entityType 을(를) 찾을 수 없습니다: $identifier"
) : DomainException(message), NotFoundMarker
