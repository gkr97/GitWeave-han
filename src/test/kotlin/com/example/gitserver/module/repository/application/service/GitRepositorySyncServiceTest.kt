package com.example.gitserver.module.repository.application.service

import com.example.gitserver.module.repository.application.command.service.GitRepositorySyncService
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class GitRepositorySyncServiceTest {

    private val repositoryRepository: RepositoryRepository = mock()
    private val branchRepository: BranchRepository = mock()
    private lateinit var service: GitRepositorySyncService

    private lateinit var repo: Repository

    @BeforeEach
    fun setUp() {
        service = GitRepositorySyncService(repositoryRepository, branchRepository)
        repo = Repository(id = 1L, owner = mock(), name = "repo", isDeleted = false)
    }

    @Test
    fun `존재하지 않는 저장소면 RepositoryNotFoundException 발생`() {
        whenever(repositoryRepository.findById(1L)).thenReturn(Optional.empty())
        assertThrows<RepositoryNotFoundException> {
            service.syncBranch(1L, "main", "commit123")
        }
    }

    @Test
    fun `newHeadCommit이 null이면 브랜치 삭제`() {
        val branch = Branch(repository = repo, name = "dev", headCommitHash = "old", isDefault = false)
        whenever(repositoryRepository.findById(repo.id)).thenReturn(Optional.of(repo))
        whenever(branchRepository.findByRepositoryIdAndName(repo.id, "dev")).thenReturn(branch)

        service.syncBranch(repo.id, "dev", null)

        verify(branchRepository, times(1)).delete(branch)
    }

    @Test
    fun `기존 브랜치가 없으면 새 브랜치 생성 및 저장, 첫 생성이면 isDefault true`() {
        whenever(repositoryRepository.findById(repo.id)).thenReturn(Optional.of(repo))
        whenever(branchRepository.findByRepositoryIdAndName(repo.id, "feature")).thenReturn(null)
        whenever(branchRepository.existsByRepositoryIdAndIsDefaultIsTrue(repo.id)).thenReturn(false)

        service.syncBranch(repo.id, "feature", "newCommitHash")

        verify(branchRepository, times(1)).save(
            argThat { name == "feature" && headCommitHash == "newCommitHash" && isDefault }
        )
    }

    @Test
    fun `기존 브랜치가 없고 이미 디폴트 브랜치가 있으면 isDefault false로 저장`() {
        whenever(repositoryRepository.findById(repo.id)).thenReturn(Optional.of(repo))
        whenever(branchRepository.findByRepositoryIdAndName(repo.id, "feature2")).thenReturn(null)
        whenever(branchRepository.existsByRepositoryIdAndIsDefaultIsTrue(repo.id)).thenReturn(true)

        service.syncBranch(repo.id, "feature2", "commit456")

        verify(branchRepository, times(1)).save(
            argThat { name == "feature2" && headCommitHash == "commit456" && !isDefault }
        )
    }

    @Test
    fun `기존 브랜치가 있으면 headCommitHash만 업데이트 후 저장`() {
        val branch = Branch(repository = repo, name = "main", headCommitHash = "oldHead", isDefault = true)
        whenever(repositoryRepository.findById(repo.id)).thenReturn(Optional.of(repo))
        whenever(branchRepository.findByRepositoryIdAndName(repo.id, "main")).thenReturn(branch)

        service.syncBranch(repo.id, "main", "newHead")

        assertEquals("newHead", branch.headCommitHash)
        verify(branchRepository, times(1)).save(branch)
    }
}
