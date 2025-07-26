package com.example.gitserver.module.repository.application.service

import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.interfaces.dto.UserSearchResponse
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserSearchService(
    private val userRepository: UserRepository,
    private val collaboratorRepository: CollaboratorRepository
) {

    /**
     * 저장소에 참여하지 않은 사용자들을 검색합니다.
     *
     * @param repoId 검색할 저장소 ID
     * @param keyword 검색 키워드 (이메일 또는 이름)
     * @return 검색 결과 사용자 목록
     */
    @Transactional
    fun searchUsers(repoId: Long, keyword: String): List<UserSearchResponse> {
        val users = userRepository.findByEmailContainingOrNameContaining(keyword, keyword)
        val collaboratorIds = collaboratorRepository.findAllByRepositoryId(repoId)
            .map { it.user.id }.toSet()

        return users.filter { !collaboratorIds.contains(it.id) }
            .map {
                UserSearchResponse(
                    id = it.id,
                    name = it.name ?: "",
                    email = it.email,
                    profileImageUrl = it.profileImageUrl
                )
            }
    }
}