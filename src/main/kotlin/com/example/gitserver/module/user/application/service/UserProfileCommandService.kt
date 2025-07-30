package com.example.gitserver.module.user.application.service

import com.example.gitserver.module.user.domain.UserRenameHistory
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRenameHistoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.s3.S3Uploader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class UserProfileCommandService(
    private val userRepository: UserRepository,
    private val userRenameHistoryRepository: UserRenameHistoryRepository,
    private val s3Uploader: S3Uploader,
) {
    /**
     * 사용자 프로필 이미지 업데이트
     * @param userId 사용자 ID
     * @param imageFile 프로필 이미지 파일
     * @return 업로드된 이미지 URL
     * @throws UserNotFoundException 사용자 ID가 유효하지 않거나 사용자를 찾을 수 없는 경우
     */
    @Transactional
    fun updateProfileImage(userId: Long, imageFile: MultipartFile): String {
        val user = userRepository.findByIdOrIdNull(userId)
            ?: throw UserNotFoundException(userId)

        val imageUrl = s3Uploader.upload(imageFile, "user-profile-pictures/$userId")

        user.updateProfileImage(imageUrl)
        return imageUrl
    }

    /**
     * 사용자 이름 업데이트
     * @param userId 사용자 ID
     * @param name 새 사용자 이름
     * @throws UserNotFoundException 사용자 ID가 유효하지 않거나 사용자를 찾을 수 없는 경우
     */
    @Transactional
    fun updateName(userId: Long, name: String) {
        val user = userRepository.findByIdOrIdNull(userId)
            ?: throw UserNotFoundException(userId)

        val oldName = user.name

        user.name = name
        userRepository.save(user)

        if (!oldName.isNullOrBlank()) {
            userRenameHistoryRepository.save(
                UserRenameHistory(
                    user = user,
                    oldUsername = oldName,
                    newUsername = name
                )
            )
        }
    }


}