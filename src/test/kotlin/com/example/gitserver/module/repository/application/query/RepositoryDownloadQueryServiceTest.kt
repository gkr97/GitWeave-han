package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.infrastructure.git.GitArchiveAdapter
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.nio.file.AccessDeniedException

class RepositoryDownloadQueryServiceTest {

    private val repositoryRepository: RepositoryRepository = mock()
    private val collaboratorRepository: CollaboratorRepository = mock()
    private val gitArchiveAdapter: GitArchiveAdapter = mock()
    private val commonCodeCacheService: CommonCodeCacheService = mock()
    private lateinit var service: RepositoryDownloadQueryService

    private lateinit var owner: User
    private lateinit var repo: Repository

    @BeforeEach
    fun setUp() {
        service = RepositoryDownloadQueryService(
            repositoryRepository,
            collaboratorRepository,
            gitArchiveAdapter,
            commonCodeCacheService
        )
        owner = User(id = 1L, email = "owner@test.com", passwordHash = "pw")
        repo = Repository(
            id = 11L,
            owner = owner,
            name = "테스트저장소",
            visibilityCodeId = 100L,
            isDeleted = false
        )
    }

    @Test
    fun `공개 저장소는 권한 없이 다운로드 가능`() {
        whenever(repositoryRepository.findByIdAndIsDeletedFalse(repo.id)).thenReturn(repo)
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY"))
            .thenReturn(
                listOf(
                    CommonCodeDetailResponse(
                        id = 100L,
                        code = "public",
                        name = "공개",
                        sortOrder = 1,
                        isActive = true
                    )
                )
            )

        val info = service.prepareDownload(repoId = repo.id, branch = "main", userId = null)
        assertEquals("테스트저장소-main.zip", info.filename)
    }

    @Test
    fun `비공개 저장소는 소유자만 다운로드 가능`() {
        whenever(repositoryRepository.findByIdAndIsDeletedFalse(repo.id)).thenReturn(repo)
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY"))
            .thenReturn(
                listOf(
                    CommonCodeDetailResponse(
                        id = 100L,
                        code = "private",
                        name = "비공개",
                        sortOrder = 2,
                        isActive = true
                    )
                )
            )

        val info = service.prepareDownload(repoId = repo.id, branch = "dev", userId = owner.id)
        assertEquals("테스트저장소-dev.zip", info.filename)
    }

    @Test
    fun `비공개 저장소 - 협업자도 다운로드 가능`() {
        val collaboratorId = 2L
        whenever(repositoryRepository.findByIdAndIsDeletedFalse(repo.id)).thenReturn(repo)
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY"))
            .thenReturn(
                listOf(
                    CommonCodeDetailResponse(
                        id = 100L,
                        code = "private",
                        name = "비공개",
                        sortOrder = 2,
                        isActive = true
                    )
                )
            )
        whenever(collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, collaboratorId)).thenReturn(true)

        val info = service.prepareDownload(repoId = repo.id, branch = "main", userId = collaboratorId)
        assertEquals("테스트저장소-main.zip", info.filename)
    }

    @Test
    fun `비공개 저장소 - 소유자, 협업자 외 접근시 AccessDeniedException`() {
        val strangerId = 3L
        whenever(repositoryRepository.findByIdAndIsDeletedFalse(repo.id)).thenReturn(repo)
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY"))
            .thenReturn(
                listOf(
                    CommonCodeDetailResponse(
                        id = 100L,
                        code = "private",
                        name = "비공개",
                        sortOrder = 2,
                        isActive = true
                    )
                )
            )
        whenever(collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, strangerId)).thenReturn(false)

        assertThrows<AccessDeniedException> {
            service.prepareDownload(repoId = repo.id, branch = "main", userId = strangerId)
        }
    }

    @Test
    fun `저장소가 없는 경우 IllegalArgumentException`() {
        whenever(repositoryRepository.findByIdAndIsDeletedFalse(repo.id)).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            service.prepareDownload(repoId = repo.id, branch = "main", userId = null)
        }
    }
}
