package com.example.gitserver.module.user.application.service

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.user.application.command.service.UserProfileCommandService
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRenameHistoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.s3.S3Uploader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.multipart.MultipartFile

class UserProfileCommandServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var s3Uploader: S3Uploader
    private lateinit var service: UserProfileCommandService
    private lateinit var userRenameHistoryRepository: UserRenameHistoryRepository

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        s3Uploader = mock()
        userRenameHistoryRepository = mock()
        service = UserProfileCommandService(userRepository,userRenameHistoryRepository, s3Uploader)
    }

    @Test
    fun `프로필 이미지 정상 업데이트`() {
        // given
        val userId = 1L
        val imageFile = mock<MultipartFile>()
        val imageUrl = "https://s3.example.com/user-profile-pictures/1/image.jpg"
        val user = UserFixture.default(id = userId)

        whenever(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(user)
        whenever(s3Uploader.upload(any(), eq("user-profile-pictures/$userId"))).thenReturn(imageUrl)

        // when
        val result = service.updateProfileImage(userId, imageFile)

        // then
        verify(userRepository).findByIdAndIsDeletedFalse(userId)
        verify(s3Uploader).upload(imageFile, "user-profile-pictures/$userId")
        assert(result == imageUrl)
        assert(user.profileImageUrl == imageUrl)
    }

    @Test
    fun `없는 유저면 프로필 업데이트 실패`() {
        // given
        val userId = 1234L
        val imageFile = mock<MultipartFile>()
        whenever(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(null)

        // when & then
        assertThrows<UserNotFoundException> {
            service.updateProfileImage(userId, imageFile)
        }
    }

    @Test
    fun `이름 정상 업데이트`() {
        // given
        val userId = 2L
        val newName = "새이름"
        val user = UserFixture.default(id = userId)

        whenever(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(user)
        whenever(userRepository.save(user)).thenReturn(user)

        // when
        service.updateName(userId, newName)

        // then
        verify(userRepository).findByIdAndIsDeletedFalse(userId)
        verify(userRepository).save(user)
        assert(user.name == newName)
    }

    @Test
    fun `없는 유저면 이름 업데이트 실패`() {
        // given
        val userId = 404L
        val newName = "없는유저"
        whenever(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(null)

        // when & then
        assertThrows<UserNotFoundException> {
            service.updateName(userId, newName)
        }
    }
}
