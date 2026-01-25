package com.example.gitserver.module.user.application.command.service

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRenameHistoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.s3.S3Uploader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

@ExtendWith(MockKExtension::class)
class UserProfileCommandServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var userRenameHistoryRepository: UserRenameHistoryRepository

    @MockK
    lateinit var s3Uploader: S3Uploader

    @InjectMockKs
    lateinit var service: UserProfileCommandService

    @Test
    fun `프로필 이미지 업데이트 성공`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            email = "test@test.com"
        )
        val imageFile = MockMultipartFile(
            "image",
            "test.png",
            "image/png",
            ByteArray(100)
        )

        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns user
        every { s3Uploader.upload(any(), any()) } returns "https://s3.url/image.png"

        // when
        val result = service.updateProfileImage(1L, imageFile)

        // then
        result shouldBe "https://s3.url/image.png"
        user.profileImageUrl shouldBe "https://s3.url/image.png"
        verify(exactly = 1) { s3Uploader.upload(any(), "user-profile-pictures/1.png") }
    }

    @Test
    fun `프로필 이미지 업데이트 실패 - 사용자 없음`() {
        // given
        val imageFile = MockMultipartFile(
            "image",
            "test.png",
            "image/png",
            ByteArray(100)
        )

        every { userRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<UserNotFoundException> {
            service.updateProfileImage(999L, imageFile)
        }
    }

    @Test
    fun `이름 업데이트 성공 - 기존 이름 있음`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            name = "기존이름"
        )

        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns user
        every { userRepository.save(any()) } returns user
        every { userRenameHistoryRepository.save(any()) } returns mockk()

        // when
        service.updateName(1L, "새이름")

        // then
        user.name shouldBe "새이름"
        verify(exactly = 1) { userRepository.save(user) }
        verify(exactly = 1) { userRenameHistoryRepository.save(any()) }
    }

    @Test
    fun `이름 업데이트 성공 - 기존 이름 없음`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            name = null
        )

        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns user
        every { userRepository.save(any()) } returns user

        // when
        service.updateName(1L, "새이름")

        // then
        user.name shouldBe "새이름"
        verify(exactly = 1) { userRepository.save(user) }
        verify(exactly = 0) { userRenameHistoryRepository.save(any()) }
    }

    @Test
    fun `이름 업데이트 실패 - 사용자 없음`() {
        // given
        every { userRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<UserNotFoundException> {
            service.updateName(999L, "새이름")
        }
    }
}
