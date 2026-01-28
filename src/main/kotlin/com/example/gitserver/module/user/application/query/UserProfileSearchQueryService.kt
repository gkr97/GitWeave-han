package com.example.gitserver.module.user.application.query

import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.repository.interfaces.dto.UserSearchResponse
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class UserProfileSearchQueryService(
    private val userRepository: UserRepository
) {
    /**
     * 사용자를 검색합니다.
     *
     * @param keyword 검색 키워드 (이메일 또는 이름)
     * @param limit 최대 검색 결과 수 (기본값: 20)
     * @param excludeUserId 제외할 사용자 ID (옵션)
     * @return 검색 결과 사용자 목록
     */
    fun search(keyword: String, limit: Int = 20, excludeUserId: Long? = null): List<UserSearchResponse> {
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()

        val users = userRepository.searchAllByKeyword(q, PageRequest.of(0, limit))
        return users
            .filter { u -> excludeUserId == null || u.id != excludeUserId }
            .map { u ->
                val nick = (u.name?.takeIf { it.isNotBlank() } ?: u.email.substringBefore("@"))
                UserSearchResponse(
                    id = u.id,
                    name = nick,
                    email = u.email,
                    profileImageUrl = u.profileImageUrl
                )
            }
    }
}
