package com.example.gitserver.module.gitindex.storage.infrastructure.git

import com.example.gitserver.module.gitindex.shared.domain.dto.MergeRequest
import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.shared.exception.*
import org.eclipse.jgit.api.errors.*
import java.nio.file.Path
import java.util.Date
import java.util.TimeZone
import org.eclipse.jgit.transport.RefSpec
import com.example.gitserver.common.exception.BusinessException
import com.example.gitserver.module.gitindex.storage.application.merge.MergeContext
import com.example.gitserver.module.gitindex.storage.application.merge.MergeStrategyRegistry
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.interfaces.dto.CloneUrlsResponse
import com.example.gitserver.module.gitindex.shared.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.exception.*
import mu.KotlinLogging
import org.eclipse.jgit.api.*
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.lib.PersonIdent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.*

@Service
class GitRepositoryAdapter(
    @Value("\${git.storage.bare-path}") private val bareRootPath: String,
    @Value("\${git.storage.workdir-path}") private val workdirRootPath: String,
    @Value("\${git.server.base-url}") private val gitBaseUrl: String,
    private val mergeStrategyRegistry: MergeStrategyRegistry
) : GitRepositoryPort {

    private val log = KotlinLogging.logger {}

    /**
     * 빈 Git 저장소를 초기화합니다.
     * @param repository 초기화할 레포지토리 정보
     * @param initializeReadme README 파일 생성 여부
     * @param gitignoreTemplate .gitignore 템플릿 (언어별)
     * @param licenseTemplate 라이선스 템플릿 (MIT, Apache 등)
     */
    override fun initEmptyGitDirectory(
        repository: Repository,
        initializeReadme: Boolean,
        gitignoreTemplate: String?,
        licenseTemplate: String?
    ) {
        val ownerId = repository.owner.id
        val repoName = repository.name

        val bareRepoPath = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git")
        val workDirPath = Paths.get(workdirRootPath, ownerId.toString(), repoName)

        log.info { "[Git] 저장소 초기화 시작 - repository=$repoName, ownerId=$ownerId" }

        if (Files.exists(bareRepoPath) || Files.exists(workDirPath)) {
            log.warn { "[Git] 저장소 디렉터리 이미 존재 - bare=$bareRepoPath, workdir=$workDirPath" }
            throw GitRepositoryAlreadyExistsException(ownerId, repoName)
        }

        try {
            Files.createDirectories(bareRepoPath.parent)
            Files.createDirectories(workDirPath.parent)

            try {
                Git.init()
                    .setDirectory(bareRepoPath.toFile())
                    .setBare(true)
                    .call()
                log.info { "[Git] bare 저장소 초기화 완료 - path=$bareRepoPath" }
            } catch (e: Exception) {
                throw GitInitializationFailedException(repository.id)
                    .also { log.error(e) { "[Git] bare init 실패" } }
            }

            bareRepoPath.resolve("git-daemon-export-ok").toFile().createNewFile()

            Git.cloneRepository()
                .setURI("file://${bareRepoPath.toAbsolutePath()}")
                .setDirectory(workDirPath.toFile())
                .call()
                .use { git ->
                    var needCommit = false

                    if (initializeReadme) {
                        workDirPath.resolve("README.md").toFile()
                            .writeText("# $repoName\n\n${repository.description ?: ""}")
                        needCommit = true
                    }
                    if (!gitignoreTemplate.isNullOrBlank()) {
                        workDirPath.resolve(".gitignore").toFile()
                            .writeText(getGitignoreTemplate(gitignoreTemplate))
                        needCommit = true
                    }
                    if (!licenseTemplate.isNullOrBlank()) {
                        workDirPath.resolve("LICENSE").toFile()
                            .writeText(getLicenseTemplate(licenseTemplate, repository.owner.name ?: "unknown"))
                        needCommit = true
                    }
                    if (!needCommit) {
                        workDirPath.resolve(".gitkeep").toFile().writeText("")
                        needCommit = true
                    }

                    if (needCommit) {
                        try {
                            git.add().addFilepattern(".").call()
                            git.commit()
                                .setMessage("Initial commit")
                                .setAuthor(
                                    PersonIdent(
                                        repository.owner.name ?: "system",
                                        repository.owner.email,
                                        Date.from(Instant.now()),
                                        TimeZone.getTimeZone("UTC")
                                    )
                                )
                                .call()
                        } catch (e: Exception) {
                            throw GitInitialCommitOrPushFailedException(
                                repository.id,
                                detail = "initial commit",
                                cause = e
                            )
                        }

                        val defaultFull = GitRefUtils.toFullRef(repository.defaultBranch)
                        val defaultShort = GitRefUtils.toShortName(defaultFull)!!

                        val headId = git.repository.resolve("HEAD")
                            ?: throw HeadCommitNotFoundException(defaultFull)

                        try {
                            git.repository.updateRef(defaultFull).apply {
                                setNewObjectId(headId)
                                setExpectedOldObjectId(null)
                                update()
                            }
                            git.repository.updateRef("HEAD").link(defaultFull)

                            git.push().call()
                            log.info { "[Git] 초기 커밋 및 푸시 완료 - repo=$repoName, branch=$defaultShort" }

                            Git.open(bareRepoPath.toFile()).use { bareGit ->
                                bareGit.repository.updateRef("HEAD").link(defaultFull)
                            }
                        } catch (e: Exception) {
                            throw GitInitialCommitOrPushFailedException(
                                repository.id,
                                detail = "set default branch / push",
                                cause = e
                            )
                        }
                    }
                }

            log.info { "[Git] 저장소 clone/초기화 완료 - from=$bareRepoPath to=$workDirPath" }

        } catch (ex: BusinessException) {
            rollbackDirectories(bareRepoPath.toString(), workDirPath.toString())
            throw ex
        } catch (e: Exception) {
            rollbackDirectories(bareRepoPath.toString(), workDirPath.toString())
            log.error(e) { "[Git] 저장소 clone 또는 초기화 실패 - 롤백 수행" }
            throw GitInitializationFailedException(repository.id)
        }
    }

    /**
     * Git 저장소 디렉토리를 삭제합니다.
     * @param repository 삭제할 레포지토리 정보
     */
    override fun deleteGitDirectories(repository: Repository) {
        val ownerId = repository.owner.id
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git")
        val workDirPath = Paths.get(workdirRootPath, ownerId.toString(), repoName)

        try {
            bareRepoPath.toFile().deleteRecursively()
            workDirPath.toFile().deleteRecursively()
            log.info { "[Git] 디렉토리 삭제 완료 - $bareRepoPath, $workDirPath" }
        } catch (e: Exception) {
            log.error(e) { "[Git] 디렉토리 삭제 실패 - $bareRepoPath" }
        }
    }

    /**
     * 레포지토리의 클론 URL을 반환합니다.
     * @param repository 대상 레포지토리
     * @return CloneUrlsResponse 클론 URL 정보
     */
    override fun getCloneUrls(repository: Repository): CloneUrlsResponse {
        val userId = repository.owner.id
        val username = repository.owner.name ?: throw OwnerNameMissingException(userId)
        val repoName = repository.name
        return CloneUrlsResponse(
            https = "$gitBaseUrl/$username/$repoName.git",
            ssh = "git@git.yourdomain:$username/$repoName.git",
            zip = "$gitBaseUrl/$username/$repoName/archive.zip"
        )
    }

    /**
     * 레포지토리의 HEAD 커밋 해시를 반환합니다.
     * @param repository 대상 레포지토리
     * @param branchName 브랜치 이름
     * @return HEAD 커밋 해시
     */
    override fun getHeadCommitHash(repository: Repository, branchName: String): String {
        val ownerId = repository.owner.id
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git")
        val fullRefName = GitRefUtils.toFullRef(branchName)

        Git.open(bareRepoPath.toFile()).use { git ->
            val ref = git.repository.exactRef(fullRefName)
                ?: throw HeadCommitNotFoundException(fullRefName)
            return ref.objectId.name
        }
    }

    /**
     * 새로운 브랜치를 생성합니다.
     * @param repository 대상 레포지토리
     * @param newBranch 새 브랜치 이름
     * @param sourceBranch 소스 브랜치 (기본값: 레포지토리의 기본 브랜치)
     */
    override fun createBranch(repository: Repository, newBranch: String, sourceBranch: String?) {
        val ownerId = repository.owner.id
        val repoId = repository.id
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git")

        Git.open(bareRepoPath.toFile()).use { git ->
            val base = sourceBranch ?: repository.defaultBranch
            val baseFull = GitRefUtils.toFullRef(base)
            val baseRef = git.repository.findRef(baseFull)
                ?: throw BranchNotFoundException(repoId, baseFull)

            val newShort = GitRefUtils.toShortName(GitRefUtils.toFullRef(newBranch))!!

            try {
                git.branchCreate()
                    .setName(newShort)
                    .setStartPoint(baseRef.objectId.name)
                    .call()
            } catch (e: RefAlreadyExistsException) {
                throw BranchAlreadyExistsException(repoId, GitRefUtils.toFullRef(newBranch))
            } catch (e: Exception) {
                throw RepositoryRenameFailedException(ownerId, repoName, repoName, e)
            }
        }
    }

    /**
     * 지정된 브랜치를 삭제합니다.
     * @param repository 대상 레포지토리
     * @param branchName 삭제할 브랜치 이름
     */
    override fun deleteBranch(repository: Repository, branchName: String) {
        val ownerId = repository.owner.id
        val repoId = repository.id
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git")

        Git.open(bareRepoPath.toFile()).use { git ->
            val full = GitRefUtils.toFullRef(branchName)
            val short = GitRefUtils.toShortName(full)!!

            git.repository.findRef(full)
                ?: throw BranchNotFoundException(repoId, full)

            try {
                git.branchDelete()
                    .setBranchNames(short)
                    .setForce(true)
                    .call()
            } catch (e: Exception) {
                throw RepositoryRenameFailedException(ownerId, repoName, repoName, e)
            }
        }
    }

    /**
     * 레포지토리 디렉토리 이름을 변경합니다.
     * @param ownerId 소유자 ID
     * @param oldName 기존 레포지토리 이름
     * @param newName 새 레포지토리 이름
     */
    override fun renameRepositoryDirectory(ownerId: Long, oldName: String, newName: String) {
        val oldBarePath = Paths.get(bareRootPath, ownerId.toString(), "$oldName.git")
        val newBarePath = Paths.get(bareRootPath, ownerId.toString(), "$newName.git")
        val oldWorkDir = Paths.get(workdirRootPath, ownerId.toString(), oldName)
        val newWorkDir = Paths.get(workdirRootPath, ownerId.toString(), newName)

        if (!Files.exists(oldBarePath)) {
            throw RepositoryDirectoryNotFoundException(ownerId, oldName)
        }
        if (Files.exists(newBarePath)) {
            throw RepositoryDirectoryAlreadyExistsException(ownerId, newName)
        }

        try {
            Files.move(oldBarePath, newBarePath)
            if (Files.exists(oldWorkDir)) {
                if (Files.exists(newWorkDir)) {
                    throw RepositoryDirectoryAlreadyExistsException(ownerId, newName)
                }
                Files.move(oldWorkDir, newWorkDir)
            }
        } catch (e: Exception) {
            throw RepositoryRenameFailedException(ownerId, oldName, newName, e)
        }
    }

    override fun merge(req: MergeRequest): String {
        val repo = req.repository
        val ownerId = repo.owner.id
        val repoName = repo.name

        val bareRepoPath = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git")
        if (!Files.exists(bareRepoPath)) {
            throw RepositoryDirectoryNotFoundException(ownerId, repoName)
        }

        val workdir: Path = Paths.get(workdirRootPath, ownerId.toString(), repoName, "merge-${UUID.randomUUID()}")
        try {
            Files.createDirectories(workdir.parent)

            val sourceFull = GitRefUtils.toFullRef(req.sourceRef)
            val targetFull = GitRefUtils.toFullRef(req.targetRef)
            val sourceShort = GitRefUtils.toShortName(sourceFull)!!
            val targetShort = GitRefUtils.toShortName(targetFull)!!

            Git.cloneRepository()
                .setURI("file://${bareRepoPath.toAbsolutePath()}")
                .setDirectory(workdir.toFile())
                .setBranchesToClone(listOf(sourceFull, targetFull))
                .setBranch(targetFull)
                .call()
                .use { git ->

                    git.branchCreate()
                        .setName(sourceShort)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint("origin/$sourceShort")
                        .setForce(true)
                        .call()

                    val ident = PersonIdent(
                        req.authorName,
                        req.authorEmail,
                        Instant.now(),
                        req.timeZone
                    )

                    git.repository.config.apply {
                        setString("user", null, "name", req.authorName)
                        setString("user", null, "email", req.authorEmail)
                        save()
                    }

                    val ctx = MergeContext(
                        git = git,
                        sourceFull = sourceFull,
                        targetFull = targetFull,
                        sourceShort = sourceShort,
                        targetShort = targetShort,
                        authorIdent = ident,
                        message = req.message
                    )
                    mergeStrategyRegistry.get(req.mergeType).execute(ctx)

                    // push target
                    git.push()
                        .setRemote("origin")
                        .setRefSpecs(RefSpec("refs/heads/$targetShort:refs/heads/$targetShort"))
                        .call()

                    return git.repository.resolve(targetFull)?.name ?: throw GitHeadNotFoundException()
                }
        } catch (e: Exception) {
            throw when (e) {
                is GitMergeConflictException,
                is GitRefNotFoundException,
                is GitMergeFailedException -> e
                is RefNotFoundException -> GitRefNotFoundException(req.sourceRef, e)
                is CheckoutConflictException,
                is GitAPIException -> GitMergeFailedException(req.sourceRef, req.targetRef, e.message, e)
                else -> GitMergeFailedException(req.sourceRef, req.targetRef, e.message, e)
            }
        } finally {
            runCatching {
                if (Files.exists(workdir)) {
                    Files.walk(workdir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }



    /**
     * 롤백 시 bare 및 work 디렉토리를 삭제합니다.
     * @param bare bare 저장소 경로
     * @param work work 디렉토리 경로
     */
    private fun rollbackDirectories(bare: String, work: String) {
        try {
            val ok = Paths.get(bare).toFile().deleteRecursively()
            if (!ok) log.warn { "[rollback] 일부 항목 삭제되지 않음: $bare" }
        } catch (e: Exception) {
            log.warn(e) { "[rollback] 삭제 실패: $bare" }
        }
        try {
            val ok = Paths.get(work).toFile().deleteRecursively()
            if (!ok) log.warn { "[rollback] 일부 항목 삭제되지 않음: $work" }
        } catch (e: Exception) {
            log.warn(e) { "[rollback] 삭제 실패: $work" }
        }
    }


    /**
     * 언어별 .gitignore 템플릿을 반환합니다.
     * @param language 언어 이름 (소문자)
     * @return .gitignore 템플릿 문자열
     */
    private fun getGitignoreTemplate(language: String): String = when (language.lowercase()) {
        "java" -> "*.class\n*.log\ntarget/\n.idea/\n"
        "node" -> "node_modules/\ndist/\n*.log\n"
        "python" -> "__pycache__/\n*.pyc\n.env\n"
        else -> "# Empty .gitignore"
    }

    /**
     * 라이선스 템플릿을 반환합니다.
     * @param type 라이선스 종류 (MIT, Apache-2.0 등)
     * @param author 저자 이름
     * @return 라이선스 템플릿 문자열
     */
    private fun getLicenseTemplate(type: String, author: String): String {
        val year = java.time.Year.now().value
        return when (type.uppercase()) {
            "MIT" -> """
                MIT License

                Copyright (c) $year $author

                Permission is hereby granted, free of charge, to any person obtaining a copy...
            """.trimIndent()
            "APACHE-2.0" -> """
                Apache License
                Version 2.0, January 2004
                http://www.apache.org/licenses/

                Copyright $year $author
            """.trimIndent()
            else -> "Custom License - $type"
        }
    }
}
