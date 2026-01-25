package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.repository.application.command.AddRepositoryStarCommand
import com.example.gitserver.module.repository.application.command.RemoveRepositoryStarCommand
import com.example.gitserver.module.repository.domain.RepositoryStar
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.exception.UserNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryStarRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException

@ExtendWith(MockKExtension::class)
class RepositoryStarCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var starRepository: RepositoryStarRepository

    @MockK
    lateinit var events: ApplicationEventPublisher

    @MockK
    lateinit var evictor: RepoCacheEvictor

    @InjectMockKs
    lateinit var handler: RepositoryStarCommandHandler

    @Test
    fun `Star 추가 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val user = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = AddRepositoryStarCommand(
            repositoryId = 1L,
            requesterId = 2L
        )
        val star = RepositoryStar(
            user = user,
            repository = repo
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns user
        every { starRepository.existsByUserIdAndRepositoryId(2L, 1L) } returns false
        every { starRepository.save(any()) } returns star
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.handle(command)

        // then
        verify(exactly = 1) { starRepository.save(any()) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `Star 추가 - 이미 Star한 경우 무시`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val user = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = AddRepositoryStarCommand(
            repositoryId = 1L,
            requesterId = 2L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns user
        every { starRepository.existsByUserIdAndRepositoryId(2L, 1L) } returns true

        // when
        handler.handle(command)

        // then
        verify(exactly = 0) { starRepository.save(any()) }
    }

    @Test
    fun `Star 추가 실패 - 저장소 없음`() {
        // given
        val command = AddRepositoryStarCommand(
            repositoryId = 999L,
            requesterId = 1L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<RepositoryNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `Star 제거 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = RemoveRepositoryStarCommand(
            repositoryId = 1L,
            requesterId = 1L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { starRepository.deleteByUserIdAndRepositoryId(1L, 1L) } just Runs
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.handle(command)

        // then
        verify(exactly = 1) { starRepository.deleteByUserIdAndRepositoryId(1L, 1L) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }
}
