package com.example.gitserver.module.gitindex.indexer.application

import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitRoutingMetrics
import com.example.gitserver.module.gitindex.storage.infrastructure.git.GitProtocolAdapter
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.springframework.context.annotation.Profile

@Service
@Profile("gitstorage")
class LagReportService(
    private val repoLocationRepository: RepoLocationRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val repositoryRepository: RepositoryRepository,
    private val branchRepository: BranchRepository,
    private val gitProtocolAdapter: GitProtocolAdapter,
    private val gitNodeRepository: GitNodeRepository,
    private val metrics: GitRoutingMetrics,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String,
    @Value("\${git.routing.lag-report.fetch-primary-enabled:false}") private val fetchPrimaryEnabled: Boolean,
    @Value("\${git.routing.lag-report.remote-url-template:}") private val remoteUrlTemplate: String,
    @Value("\${git.routing.lag-report.use-owner-name:false}") private val useOwnerName: Boolean
) {
    private val log = KotlinLogging.logger {}

    /**
     * 모든 저장소 위치에 대해 지연 시간을 보고합니다.
     */
    fun reportAll() {
        repoLocationRepository.findAll().forEach { location ->
            val replicaIds = repoReplicaRepository.findByIdRepoId(location.repoId)
                .map { it.id.nodeId }
            if (!replicaIds.contains(localNodeId)) return@forEach

            val repo = repositoryRepository.findById(location.repoId).orElse(null) ?: return@forEach
            val branch = branchRepository.findByRepositoryIdAndName(repo.id, repo.defaultBranch)
                ?: return@forEach

            try {
                val repository = gitProtocolAdapter.openRepository(repo.owner.id, repo.name)
                val repoDir = repository.directory
                val primaryHashFromRemote = fetchPrimaryHeadIfEnabled(
                    location,
                    repo.owner.id,
                    repo.owner.name,
                    repo.name,
                    repo.defaultBranch,
                    repoDir
                )
                val ref = repository.exactRef("refs/heads/${repo.defaultBranch}")
                if (ref == null) {
                    updateHealth(location, localNodeId, "degraded", Long.MAX_VALUE, Long.MAX_VALUE)
                    return@forEach
                }

                val localCommitTime = RevWalk(repository).use { rw ->
                    val commit = rw.parseCommit(ref.objectId)
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(commit.commitTime.toLong()),
                        ZoneId.systemDefault()
                    )
                }

                val primaryCommitHash = primaryHashFromRemote ?: branch.headCommitHash
                val localHash = ref.objectId.name
                val lagCommits = computeCommitLag(repository, localHash, primaryCommitHash)
                val lagMs = computeTimeLagMs(repository, localHash, primaryCommitHash, localCommitTime, branch.lastCommitAt)

                updateHealth(location, localNodeId, "healthy", lagMs, lagCommits)
                metrics.recordLag(location.repoId, localNodeId, lagCommits, lagMs)
            } catch (e: Exception) {
                log.warn(e) { "[LagReporter] failed repoId=${location.repoId}" }
                updateHealth(location, localNodeId, "degraded", Long.MAX_VALUE, Long.MAX_VALUE)
            }
        }
    }

    private fun updateHealth(
        location: RepoLocationEntity,
        nodeId: String,
        health: String,
        lagMs: Long,
        lagCommits: Long
    ) {
        val existing = repoReplicaRepository.findByIdRepoIdAndIdNodeId(location.repoId, nodeId)
        if (existing == null) {
            repoReplicaRepository.save(
                com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity(
                    id = com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId(
                        repoId = location.repoId,
                        nodeId = nodeId
                    ),
                    health = health,
                    lagMs = lagMs,
                    lagCommits = lagCommits
                )
            )
        } else {
            existing.health = health
            existing.lagMs = lagMs
            existing.lagCommits = lagCommits
            existing.updatedAt = LocalDateTime.now()
            repoReplicaRepository.save(existing)
        }
    }

    private fun computeCommitLag(
        repository: org.eclipse.jgit.lib.Repository,
        localHash: String,
        primaryHash: String?
    ): Long {
        if (primaryHash.isNullOrBlank() || primaryHash == localHash) return 0L

        val primaryId = ObjectId.fromString(primaryHash)
        if (!repository.objectDatabase.has(primaryId)) return Long.MAX_VALUE

        val localId = ObjectId.fromString(localHash)
        return RevWalk(repository).use { rw ->
            val primaryCommit = rw.parseCommit(primaryId)
            val localCommit = rw.parseCommit(localId)
            rw.markStart(primaryCommit)
            rw.markUninteresting(localCommit)
            var count = 0L
            while (true) {
                val next: RevCommit = rw.next() ?: break
                count++
            }
            count
        }
    }

    private fun computeTimeLagMs(
        repository: org.eclipse.jgit.lib.Repository,
        localHash: String,
        primaryHash: String?,
        localCommitTime: LocalDateTime,
        fallbackPrimaryTime: LocalDateTime?
    ): Long {
        if (primaryHash.isNullOrBlank() || primaryHash == localHash) return 0L
        val primaryId = ObjectId.fromString(primaryHash)
        if (repository.objectDatabase.has(primaryId)) {
            val primaryTime = RevWalk(repository).use { rw ->
                val commit = rw.parseCommit(primaryId)
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(commit.commitTime.toLong()),
                    ZoneId.systemDefault()
                )
            }
            val diff = Duration.between(localCommitTime, primaryTime)
            return diff.toMillis().coerceAtLeast(0)
        }
        if (fallbackPrimaryTime != null) {
            val diff = Duration.between(localCommitTime, fallbackPrimaryTime)
            return diff.toMillis().coerceAtLeast(0)
        }
        return Long.MAX_VALUE
    }

    private fun fetchPrimaryHeadIfEnabled(
        location: RepoLocationEntity,
        ownerId: Long,
        ownerName: String?,
        repoName: String,
        branch: String,
        repoDir: java.io.File
    ): String? {
        if (!fetchPrimaryEnabled || remoteUrlTemplate.isBlank()) return null
        val primaryNode = gitNodeRepository.findById(location.primaryNodeId).orElse(null) ?: return null
        val remoteUrl = buildRemoteUrl(primaryNode.host, ownerId, ownerName, repoName)
        val refName = "refs/remotes/primary/$branch"
        val cmd = listOf(
            "git", "-C", repoDir.absolutePath,
            "fetch", "--prune", remoteUrl,
            "+refs/heads/$branch:$refName"
        )
        val result = runCommand(cmd)
        if (!result) return null
        return runCatching {
            val ref = gitProtocolAdapter.openRepository(ownerId, repoName).exactRef(refName)
            ref?.objectId?.name
        }.getOrNull()
    }

    private fun buildRemoteUrl(host: String, ownerId: Long, ownerName: String?, repo: String): String {
        val owner = if (useOwnerName && !ownerName.isNullOrBlank()) ownerName else ownerId.toString()
        return remoteUrlTemplate
            .replace("{host}", host)
            .replace("{ownerId}", ownerId.toString())
            .replace("{ownerName}", ownerName ?: ownerId.toString())
            .replace("{owner}", owner)
            .replace("{repo}", repo)
    }

    private fun runCommand(cmd: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            if (exit != 0) {
                log.warn { "[LagReporter] fetch failed exit=$exit output=${output.take(1000)}" }
            }
            exit == 0
        } catch (e: Exception) {
            log.warn(e) { "[LagReporter] fetch failed" }
            false
        }
    }

}
