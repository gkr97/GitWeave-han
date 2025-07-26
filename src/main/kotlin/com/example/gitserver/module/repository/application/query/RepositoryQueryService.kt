package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.domain.service.CommitService
import com.example.gitserver.module.gitindex.domain.service.ReadmeService
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.application.service.GitService
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.*
import com.example.gitserver.module.repository.interfaces.dto.*
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.StructuredTaskScope

private val log = KotlinLogging.logger {}

@Service
class RepositoryQueryService(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val branchRepository: BranchRepository,
    private val repositoryStatsRepository: RepositoryStatsRepository,
    private val gitService: GitService,
    private val commitService: CommitService,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val readmeService: ReadmeService,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getRepository(repoId: Long, branch: String? = null, currentUserId: Long?): RepoDetailResponse {
        log.info { "[getRepository] repoId=$repoId, branch=${branch ?: "default"} 시작" }

        val repo = repositoryRepository.findByIdWithOwner(repoId)
            ?: throw RepositoryNotFoundException(repoId)

        val visibility = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.id == repo.visibilityCodeId }?.code ?: "unknown"

        if (visibility == "private") {
            val isOwner = repo.owner.id == currentUserId
            val isCollaborator = currentUserId?.let {
                collaboratorRepository.existsByRepositoryIdAndUserId(repoId, it)
            } ?: false
            if (!isOwner && !isCollaborator) {
                throw RepositoryAccessDeniedException(repoId, currentUserId)
            }
        }

        val targetBranchName = branch ?: repo.defaultBranch
        if (branchRepository.findByRepositoryIdAndName(repoId, targetBranchName) == null) {
            throw RepositoryNotFoundException(repoId)
        }

        val rawCommit = commitService.getLatestCommitHash(repoId, targetBranchName)
            ?: throw RepositoryNotFoundException(repoId)
        val mainCommit = rawCommit.copy(author = enrichAuthor(rawCommit.author))

        val owner = RepositoryUserResponse(
            userId = repo.owner.id,
            nickname = repo.owner.name ?: "이름 없음",
            profileImageUrl = repo.owner.profileImageUrl
        )

        val contributors = collaboratorRepository.findAllAcceptedByRepositoryId(repoId).map {
            RepositoryUserResponse(
                userId = it.user.id,
                nickname = it.user.name ?: "알 수 없음",
                profileImageUrl = it.user.profileImageUrl
            )
        }

        val openPrCount = pullRequestRepository.countByRepositoryIdAndClosedFalse(repoId)

        val readmeInfo: ReadmeResponse
        val readmeContent: String
        val readmeHtml: String
        val languageStats: List<LanguageStatResponse>
        val cloneUrls: CloneUrlsResponse
        val stats = repositoryStatsRepository.findById(repoId).orElse(null)

        StructuredTaskScope.ShutdownOnFailure().use { scope ->
            val readmeInfoFork = scope.fork {
                log.debug { "[Scope] readmeInfo 시작" }
                readmeService.getReadmeInfo(repoId, mainCommit.hash)
            }

            val readmeContentFork = scope.fork {
                log.debug { "[Scope] readmeContent 시작" }
                readmeService.getReadmeContent(repoId, mainCommit.hash)
            }

            val readmeHtmlFork = scope.fork {
                log.debug { "[Scope] readmeHtml 시작" }
                readmeService.getReadmeHtml(repoId, mainCommit.hash)
            }

            val languageStatsFork = scope.fork {
                log.debug { "[Scope] languageStats 시작" }
                readmeService.getLanguageStats(repoId)
            }

            val cloneUrlsFork = scope.fork {
                log.debug { "[Scope] cloneUrls 시작" }
                gitService.getCloneUrls(repoId)
            }

            scope.join()
            scope.throwIfFailed()

            readmeInfo = readmeInfoFork.get()
            readmeContent = readmeContentFork.get().toString()
            readmeHtml = readmeHtmlFork.get().toString()
            languageStats = languageStatsFork.get()
            cloneUrls = cloneUrlsFork.get()
        }

        val readme = ReadmeResponse(
            exists = readmeInfo.exists,
            path = readmeInfo.path,
            content = readmeContent,
            html = readmeHtml
        )

        val branches = branchRepository.findAllByRepositoryId(repoId).map {
            val commit = commitService.getLatestCommitHash(repoId, it.name)
                ?: throw RepositoryNotFoundException(repoId)
            val enriched = commit.copy(author = enrichAuthor(commit.author))

            BranchResponse(
                name = it.name,
                isDefault = it.name == repo.defaultBranch,
                isProtected = it.isProtected,
                createdAt = it.createdAt,
                headCommit = enriched
            )
        }

        val statsResponse = stats?.let {
            RepositoryStatsResponse(
                stars = it.stars,
                forks = it.forks,
                watchers = it.watchers,
                issues = it.issues,
                pullRequests = it.pullRequests,
                lastCommitAt = it.lastCommitAt
            )
        } ?: RepositoryStatsResponse(0, 0, 0, 0, 0, null)

        val fileTree = commitService.getFileTreeAtRoot(repoId, mainCommit.hash)

        return RepoDetailResponse(
            id = repo.id,
            name = repo.name,
            description = repo.description,
            visibility = visibility,
            createdAt = repo.createdAt,
            lastUpdatedAt = repo.updatedAt,
            owner = owner,
            isOwner = false,
            isBookmarked = false,
            isInvited = false,
            contributors = contributors,
            openPrCount = openPrCount,
            languageStats = languageStats,
            cloneUrls = cloneUrls,
            readme = readme,
            defaultBranch = repo.defaultBranch,
            branches = branches,
            stats = statsResponse,
            fileTree = fileTree
        )
    }

    fun isOwner(repoId: Long, userId: Long?): Boolean {
        return userId != null && repositoryRepository.findByIdWithOwner(repoId)?.owner?.id == userId
    }

    fun isBookmarked(repoId: Long, userId: Long?): Boolean {
        return userId?.let { bookmarkRepository.existsByUserIdAndRepositoryId(it, repoId) } ?: false
    }

    fun isInvited(repoId: Long, userId: Long?): Boolean {
        return userId?.let { collaboratorRepository.existsByRepositoryIdAndUserId(repoId, it) } ?: false
    }

    private fun enrichAuthor(user: RepositoryUserResponse): RepositoryUserResponse {
        val userEntity = userRepository.findById(user.userId).orElse(null)
        return userEntity?.let {
            RepositoryUserResponse(
                userId = it.id,
                nickname = it.name ?: "알 수 없음",
                profileImageUrl = it.profileImageUrl
            )
        } ?: user
    }
}
