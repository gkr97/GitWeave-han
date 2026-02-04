package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicationExecutor(
    private val repositoryRepository: RepositoryRepository,
    @Value("\${git.replication.remote-url-template:}") private val remoteUrlTemplate: String,
    @Value("\${git.replication.target-bare-path:}") private val targetBarePath: String,
    @Value("\${git.replication.use-owner-name:false}") private val useOwnerName: Boolean
) {
    private val log = KotlinLogging.logger {}

    fun replicate(task: GitReplicationTaskEntity, sourceHost: String, forceRebuild: Boolean): ReplicationResult {
        if (remoteUrlTemplate.isBlank()) {
            return ReplicationResult(false, "remote-url-template not configured")
        }

        val repo = repositoryRepository.findById(task.repoId).orElse(null)
            ?: return ReplicationResult(false, "repository not found")

        val ownerId = repo.owner.id
        val ownerName = repo.owner.name ?: ownerId.toString()
        val repoName = repo.name

        val basePath = if (targetBarePath.isBlank()) {
            return ReplicationResult(false, "target-bare-path not configured")
        } else {
            targetBarePath
        }

        val remoteUrl = buildRemoteUrl(sourceHost, ownerId, ownerName, repoName)
        val targetPath = File("$basePath/$ownerId/$repoName.git")

        if (forceRebuild && targetPath.exists()) {
            deleteRecursive(targetPath)
        }

        val cmd = if (!targetPath.exists()) {
            listOf("git", "clone", "--mirror", remoteUrl, targetPath.absolutePath)
        } else {
            listOf("git", "-C", targetPath.absolutePath, "fetch", "--prune", "origin")
        }

        return runCommand(cmd)
    }

    private fun buildRemoteUrl(sourceHost: String, ownerId: Long, ownerName: String, repo: String): String {
        val owner = if (useOwnerName) ownerName else ownerId.toString()
        return remoteUrlTemplate
            .replace("{host}", sourceHost)
            .replace("{ownerId}", ownerId.toString())
            .replace("{ownerName}", ownerName)
            .replace("{owner}", owner)
            .replace("{repo}", repo)
    }

    private fun runCommand(cmd: List<String>): ReplicationResult {
        return try {
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            if (exit == 0) {
                ReplicationResult(true, null)
            } else {
                log.warn { "[Replication] failed exit=$exit output=$output" }
                ReplicationResult(false, output.take(2000))
            }
        } catch (e: Exception) {
            ReplicationResult(false, e.message)
        }
    }

    private fun deleteRecursive(path: File) {
        if (!path.exists()) return
        path.walkBottomUp().forEach { it.delete() }
    }
}

data class ReplicationResult(
    val success: Boolean,
    val error: String?
)
