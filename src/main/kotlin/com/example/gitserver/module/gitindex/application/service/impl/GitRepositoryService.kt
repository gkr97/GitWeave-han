package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.module.gitindex.application.service.GitService
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.interfaces.dto.CloneUrlsResponse
import com.example.gitserver.common.util.GitRefUtils
import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.*

@Service
class GitRepositoryService(
    @Value("\${git.storage.bare-path}") private val bareRootPath: String,
    @Value("\${git.storage.workdir-path}") private val workdirRootPath: String,
    @Value("\${git.server.base-url}") private val gitBaseUrl: String,
) : GitService {

    private val log = KotlinLogging.logger {}

    /**
     * 저장소를 초기화하고 README, .gitignore, LICENSE 파일을 생성합니다.
     */
    override fun initEmptyGitDirectory(
        repository: Repository,
        initializeReadme: Boolean,
        gitignoreTemplate: String?,
        licenseTemplate: String?
    ) {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name

        val bareRepoPath = Paths.get(bareRootPath, ownerId, "$repoName.git")
        val workDirPath = Paths.get(workdirRootPath, ownerId, repoName)

        log.info { "[Git] 저장소 초기화 시작 - repository=$repoName, ownerId=$ownerId" }

        if (Files.exists(bareRepoPath) || Files.exists(workDirPath)) {
            log.warn { "[Git] 저장소 디렉토리 이미 존재 - bare=$bareRepoPath, workdir=$workDirPath" }
            throw IllegalStateException("이미 존재하는 저장소입니다: $repoName")
        }

        Files.createDirectories(bareRepoPath.parent)
        Files.createDirectories(workDirPath.parent)

        Git.init()
            .setDirectory(bareRepoPath.toFile())
            .setBare(true)
            .call()
        log.info { "[Git] bare 저장소 초기화 완료 - path=$bareRepoPath" }

        bareRepoPath.resolve("git-daemon-export-ok").toFile().createNewFile()

        try {
            val git = Git.cloneRepository()
                .setURI("file://${bareRepoPath.toAbsolutePath()}")
                .setDirectory(workDirPath.toFile())
                .call()

            var needCommit = false

            if (initializeReadme) {
                val readme = workDirPath.resolve("README.md").toFile()
                readme.writeText("# $repoName\n\n${repository.description ?: ""}")
                needCommit = true
            }

            if (!gitignoreTemplate.isNullOrBlank()) {
                val gitignore = workDirPath.resolve(".gitignore").toFile()
                gitignore.writeText(getGitignoreTemplate(gitignoreTemplate))
                needCommit = true
            }

            if (!licenseTemplate.isNullOrBlank()) {
                val license = workDirPath.resolve("LICENSE").toFile()
                license.writeText(getLicenseTemplate(licenseTemplate, repository.owner.name ?: "unknown"))
                needCommit = true
            }

            if (!needCommit) {
                val dummy = workDirPath.resolve(".gitkeep").toFile()
                dummy.writeText("")
                needCommit = true
            }

            if (needCommit) {
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

                val defaultFull = GitRefUtils.toFullRef(repository.defaultBranch)
                val defaultShort = GitRefUtils.toShortName(defaultFull)!!

                val headId = git.repository.resolve("HEAD")
                    ?: throw IllegalStateException("HEAD 참조를 찾을 수 없습니다")

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
            }

            log.info { "[Git] 저장소 clone 완료 - from=$bareRepoPath to=$workDirPath" }

        } catch (e: Exception) {
            log.error(e) { "[Git] 저장소 clone 또는 초기화 실패 - 롤백 수행" }
            bareRepoPath.toFile().deleteRecursively()
            workDirPath.toFile().deleteRecursively()
            throw IllegalStateException("Git 초기화 실패: ${e.message}")
        }
    }

    override fun deleteGitDirectories(repository: Repository) {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId, "$repoName.git")
        val workDirPath = Paths.get(workdirRootPath, ownerId, repoName)

        try {
            bareRepoPath.toFile().deleteRecursively()
            workDirPath.toFile().deleteRecursively()
            log.info { "[Git] 디렉토리 삭제 완료 - $bareRepoPath, $workDirPath" }
        } catch (e: Exception) {
            log.error(e) { "[Git] 디렉토리 삭제 실패 - $bareRepoPath" }
        }
    }

    override fun getCloneUrls(repository: Repository): CloneUrlsResponse {
        val username = repository.owner.name ?: throw IllegalStateException("owner name 없음")
        val repoName = repository.name
        return CloneUrlsResponse(
            https = "$gitBaseUrl/$username/$repoName.git",
            ssh = "git@git.yourdomain:$username/$repoName.git",
            zip = "$gitBaseUrl/$username/$repoName/archive.zip"
        )
    }

    /**
     * 지정된 브랜치의 HEAD 커밋 해시
     */
    override fun getHeadCommitHash(repository: Repository, branchName: String): String {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId, "$repoName.git")

        val fullRefName = GitRefUtils.toFullRef(branchName)

        Git.open(bareRepoPath.toFile()).use { git ->
            val ref = git.repository.exactRef(fullRefName)
                ?: throw IllegalStateException("브랜치 '$fullRefName'에 대한 참조가 없습니다.")
            return ref.objectId.name
        }
    }

    private fun getGitignoreTemplate(language: String): String {
        return when (language.lowercase()) {
            "java" -> "*.class\n*.log\ntarget/\n.idea/\n"
            "node" -> "node_modules/\ndist/\n*.log\n"
            "python" -> "__pycache__/\n*.pyc\n.env\n"
            else -> "# Empty .gitignore"
        }
    }

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

    /**
     * 새 브랜치 생성
     */
    override fun createBranch(repository: Repository, newBranch: String, sourceBranch: String?) {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId, "$repoName.git")

        Git.open(bareRepoPath.toFile()).use { git ->
            val base = sourceBranch ?: repository.defaultBranch
            val baseFull = GitRefUtils.toFullRef(base)
            val baseRef = git.repository.findRef(baseFull)
                ?: throw IllegalArgumentException("브랜치 없음: $baseFull")

            val newShort = GitRefUtils.toShortName(GitRefUtils.toFullRef(newBranch))!!

            git.branchCreate()
                .setName(newShort)
                .setStartPoint(baseRef.objectId.name)
                .call()
        }
    }

    /**
     * 브랜치 삭제
     */
    override fun deleteBranch(repository: Repository, branchName: String) {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId, "$repoName.git")

        Git.open(bareRepoPath.toFile()).use { git ->
            val full = GitRefUtils.toFullRef(branchName)
            val short = GitRefUtils.toShortName(full)!!

            git.repository.findRef(full)
                ?: throw IllegalArgumentException("브랜치 없음: $full")

            git.branchDelete()
                .setBranchNames(short)
                .setForce(true)
                .call()
        }
    }

    /**
     * 저장소 디렉토리 이름을 변경합니다.
     * @param ownerId 소유자 ID
     * @param oldName 기존 저장소 이름
     * @param newName 새 저장소 이름
     */
    override fun renameRepositoryDirectory(ownerId: Long, oldName: String, newName: String) {
        val oldBarePath = Paths.get(bareRootPath, ownerId.toString(), "$oldName.git")
        val newBarePath = Paths.get(bareRootPath, ownerId.toString(), "$newName.git")
        if (!Files.exists(oldBarePath)) {
            throw IllegalStateException("기존 저장소 없음: $oldBarePath")
        }
        if (Files.exists(newBarePath)) {
            throw IllegalStateException("이미 새 이름의 저장소 존재: $newBarePath")
        }
        Files.move(oldBarePath, newBarePath)

        val oldWorkDir = Paths.get(workdirRootPath, ownerId.toString(), oldName)
        val newWorkDir = Paths.get(workdirRootPath, ownerId.toString(), newName)
        if (Files.exists(oldWorkDir)) {
            Files.move(oldWorkDir, newWorkDir)
        }
    }

}
