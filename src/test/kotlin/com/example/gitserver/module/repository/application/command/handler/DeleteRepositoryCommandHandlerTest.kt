package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.module.repository.application.command.DeleteRepositoryCommand
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.NotRepositoryOwnerException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class DeleteRepositoryCommandHandlerTest {

    private val repositoryRepository: RepositoryRepository = mock()
    private val userRepository: UserRepository = mock()
    private lateinit var handler: DeleteRepositoryCommandHandler

    private lateinit var user: User
    private lateinit var repo: Repository

    @BeforeEach
    fun setUp() {
        handler = DeleteRepositoryCommandHandler(repositoryRepository, userRepository)
        user = UserFixture.default()
        repo = RepositoryFixture.default(owner = user)
    }

    @Test
    fun `정상적으로 저장소를 삭제할 수 있음`() {
        val command = DeleteRepositoryCommand(repositoryId = repo.id, requesterEmail = user.email)
        whenever(userRepository.findByEmailAndIsDeletedFalse(user.email)).thenReturn(user)
        whenever(repositoryRepository.findById(repo.id)).thenReturn(Optional.of(repo))
        whenever(repositoryRepository.save(any<Repository>())).thenReturn(repo)

        handler.handle(command)

        assertTrue(repo.isDeleted)
    }

    @Test
    fun `인증된 사용자가 아닌 경우 예외 발생`() {
        val command = DeleteRepositoryCommand(repositoryId = repo.id, requesterEmail = user.email)
        whenever(userRepository.findByEmailAndIsDeletedFalse(user.email)).thenReturn(null)

        val exception = assertThrows<IllegalArgumentException> {
            handler.handle(command)
        }
        assertTrue(exception.message!!.contains("인증된 사용자가 없음"))
    }

    @Test
    fun `존재하지 않는 저장소에  대해 예외 발생`() {
        val command = DeleteRepositoryCommand(repositoryId = 999L, requesterEmail = user.email)
        whenever(userRepository.findByEmailAndIsDeletedFalse(user.email)).thenReturn(user)
        whenever(repositoryRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<RepositoryNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `저장소 소유자가 아닌 경우 예외 발생`() {
        val anotherUser = UserFixture.default(id = 2L, email = "other@test.com")
        val repoOtherOwner = RepositoryFixture.default(id = 101L, owner = anotherUser)
        val command = DeleteRepositoryCommand(repositoryId = repoOtherOwner.id, requesterEmail = user.email)
        whenever(userRepository.findByEmailAndIsDeletedFalse(user.email)).thenReturn(user)
        whenever(repositoryRepository.findById(repoOtherOwner.id)).thenReturn(Optional.of(repoOtherOwner))

        assertThrows<NotRepositoryOwnerException> {
            handler.handle(command)
        }
    }

    @Test
    fun `이미 삭제된 저장소인 경우 예외 발생`() {
        val deletedRepo = RepositoryFixture.default(owner = user, isDeleted = true)
        val command = DeleteRepositoryCommand(repositoryId = deletedRepo.id, requesterEmail = user.email)
        whenever(userRepository.findByEmailAndIsDeletedFalse(user.email)).thenReturn(user)
        whenever(repositoryRepository.findById(deletedRepo.id)).thenReturn(Optional.of(deletedRepo))

        val ex = assertThrows<IllegalStateException> {
            handler.handle(command)
        }
        assertEquals("이미 삭제된 저장소입니다.", ex.message)
    }
}
