package com.example.gitserver.module.repository.application.service.impl

import com.example.gitserver.module.repository.application.service.GitService
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.interfaces.dto.CloneUrlsResponse
import com.example.gitserver.module.repository.interfaces.dto.LanguageStatResponse
import com.example.gitserver.module.repository.interfaces.dto.ReadmeResponse
import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.*

private val log = KotlinLogging.logger {}

@Service
class JGitService(
    @Value("\${git.storage.bare-path}") private val bareRootPath: String,
    @Value("\${git.storage.workdir-path}") private val workdirRootPath: String,
    @Value("\${cloud.aws.s3.bucket}") private var bucketName: String,
    @Value("\${cloud.aws.s3.endpoint}") private val s3EndPoint: String,
) : GitService {

    /**
     * * 저장소를 초기화하고 README, .gitignore, LICENSE 파일을 생성합니다.
     * @param repository 초기화할 저장소 정보
     * @param initializeReadme README 파일 생성 여부
     * @param gitignoreTemplate .gitignore 템플릿 이름
     * @param licenseTemplate LICENSE 템플릿 이름
     */
    override fun initEmptyGitDirectory(
        repository: Repository,
        initializeReadme: Boolean,
        gitignoreTemplate: String?,
        licenseTemplate: String?
    ) {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name

        val bareRepoPath = Paths.get(bareRootPath, ownerId, repoName)
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

                val defaultBranch = repository.defaultBranch
                val headId = git.repository.resolve("HEAD")
                    ?: throw IllegalStateException("HEAD 참조를 찾을 수 없습니다")

                val branchRefUpdate = git.repository.updateRef("refs/heads/$defaultBranch")
                branchRefUpdate.setNewObjectId(headId)
                branchRefUpdate.setExpectedOldObjectId(null)
                branchRefUpdate.update()

                git.repository.updateRef("HEAD").link("refs/heads/$defaultBranch")

                git.push().call()
                log.info { "[Git] 초기 커밋 및 푸시 완료 - repo=$repoName, branch=$defaultBranch" }
            }

            log.info { "[Git] 저장소 clone 완료 - from=$bareRepoPath to=$workDirPath" }

        } catch (e: Exception) {
            log.error(e) { "[Git] 저장소 clone 또는 초기화 실패 - 롤백 수행" }
            bareRepoPath.toFile().deleteRecursively()
            workDirPath.toFile().deleteRecursively()
            throw IllegalStateException("Git 초기화 실패: ${e.message}")
        }
    }

    /**
     * 저장소의 언어 통계 정보를 반환합니다.
     * @param repository 대상 저장소
     * @return 언어 통계 정보
     */
    override fun deleteGitDirectories(repository: Repository) {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId, repoName)
        val workDirPath = Paths.get(workdirRootPath, ownerId, repoName)

        try {
            bareRepoPath.toFile().deleteRecursively()
            workDirPath.toFile().deleteRecursively()
            log.info { "[Git] 디렉토리 삭제 완료 - $bareRepoPath, $workDirPath" }
        } catch (e: Exception) {
            log.error(e) { "[Git] 디렉토리 삭제 실패 - $bareRepoPath" }
        }
    }
    /**
     * 저장소의 언어 통계 정보를 반환합니다.
     * @param repository 대상 저장소
     * @return 언어 통계 정보
     */
    override fun getCloneUrls(repoId: Long): CloneUrlsResponse {
        return CloneUrlsResponse(
            https = "$s3EndPoint/$bucketName/repos/$repoId.git",
            ssh = "git@gitserver.local:$repoId.git",
            zip = "$s3EndPoint/$bucketName/repos/$repoId/archive.zip"
        )
    }

    override fun getHeadCommitHash(repository: Repository, branchName: String): String {
        val ownerId = repository.owner.id.toString()
        val repoName = repository.name
        val bareRepoPath = Paths.get(bareRootPath, ownerId, repoName)
        Git.open(bareRepoPath.toFile()).use { git ->
            val ref = git.repository.exactRef("refs/heads/$branchName")
                ?: throw IllegalStateException("브랜치 '$branchName'에 대한 참조가 없습니다.")
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
}
