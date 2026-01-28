package com.example.gitserver.module.search.application.service

import com.example.gitserver.module.search.interfaces.dto.UserSearchDto
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class UserSearchQueryService(
    private val userRepository: UserRepository
) {
    /**
     * 사용자를 검색합니다.
     *
     * @param q 검색 키워드
     * @param from 시작 인덱스
     * @param size 페이지 크기
     * @return 검색 결과 사용자 목록과 총 개수
     */
    fun searchUsers(
        q: String,
        from: Int,
        size: Int
    ): Pair<List<UserSearchDto>, Long> {

        if (q.isBlank()) return Pair(emptyList(), 0)

        val page = PageRequest.of(from / size, size)

        val users = userRepository.searchAllByKeyword(q, page)
        val total = userRepository.countByKeyword(q)

        val items = users.map {
            UserSearchDto(
                id = it.id,
                username = it.name ?: it.email,
                avatarUrl = it.profileImageUrl,
                repoCount = null
            )
        }

        return Pair(items, total)
    }
}
