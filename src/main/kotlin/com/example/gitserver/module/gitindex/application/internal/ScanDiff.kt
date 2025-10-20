package com.example.gitserver.module.gitindex.application.internal

import com.example.gitserver.module.gitindex.infrastructure.runtime.ExecutorGateway
import mu.KotlinLogging
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Collections
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

@Component
class ScanDiff(
    private val processor: BlobProcessor,
    private val exec: ExecutorGateway,
) {
    companion object { private const val MAX_CONCURRENCY = 20 }

    /**
     * 커밋의 변경된 파일들을 스캔하여 처리한다.
     */
    fun scan(
        repositoryId: Long,
        commit: RevCommit,
        repo: Repository,
        commitInstant: Instant
    ): IndexFailures {
        val parents = commit.parents
        val newTreeId = commit.tree.id

        if (parents.isEmpty()) {
            log.debug { "[DiffScan] parents=0 → skip repo=$repositoryId commit=${commit.name}" }
            return IndexFailures(0, emptyList())
        }

        if (parents.size == 1) {
            return twoWayDiff(
                repositoryId = repositoryId,
                commitHash = commit.name,
                oldTreeId = parents[0].tree.id,
                newTreeId = newTreeId,
                repo = repo,
                commitInstant = commitInstant
            )
        }

        val base = findNWayMergeBase(repo, parents.map { it.id })
        return if (base != null) {
            log.debug { "[DiffScan] 3-way base=${base.name} repo=$repositoryId commit=${commit.name}" }
            twoWayDiff(
                repositoryId = repositoryId,
                commitHash = commit.name,
                oldTreeId = base.tree.id,
                newTreeId = newTreeId,
                repo = repo,
                commitInstant = commitInstant
            )
        } else {
            log.debug { "[DiffScan] base not found → union repo=$repositoryId commit=${commit.name}" }
            unionParentsDiff(
                repositoryId = repositoryId,
                commitHash = commit.name,
                parentTrees = parents.map { it.tree.id },
                newTreeId = newTreeId,
                repo = repo,
                commitInstant = commitInstant
            )
        }
    }

    /**
     * 두 커밋의 공통 조상을 찾는다.
     */
    private fun findMergeBase(repo: Repository, a: ObjectId, b: ObjectId): RevCommit? =
        RevWalk(repo).use { rw ->
            rw.reset()
            rw.revFilter = RevFilter.MERGE_BASE
            rw.markStart(rw.parseCommit(a))
            rw.markStart(rw.parseCommit(b))
            rw.next()
        }

    /**
     * 여러 커밋의 공통 조상을 찾는다.
     * - 공통 조상이 없으면 null을 반환한다.
     * - 커밋이 하나면 해당 커밋을 반환한다.
     * - 커밋이 없으면 null을 반환한다.
     */
    private fun findNWayMergeBase(repo: Repository, ids: List<ObjectId>): RevCommit? {
        if (ids.isEmpty()) return null
        if (ids.size == 1) return RevWalk(repo).use { it.parseCommit(ids[0]) }
        var base: RevCommit? = findMergeBase(repo, ids[0], ids[1]) ?: return null
        for (i in 2 until ids.size) {
            base = findMergeBase(repo, base!!.id, ids[i]) ?: return null
        }
        return base
    }

    /**
     * 두 트리의 차이를 계산하고, 변경된 파일들을 처리한다.
     */
    private fun twoWayDiff(
        repositoryId: Long,
        commitHash: String,
        oldTreeId: ObjectId,
        newTreeId: ObjectId,
        repo: Repository,
        commitInstant: Instant
    ): IndexFailures {
        log.debug { "[DiffScan] 2-way repo=$repositoryId commit=$commitHash old=${oldTreeId.name} new=${newTreeId.name}" }
        val diffs = computeDiff(repo, oldTreeId, newTreeId)
        val result = processDiffs(repositoryId, commitHash, repo, commitInstant, diffs)

        return result
    }

    /**
     * 여러 부모 커밋의 변경 사항을 모두 합친다.
     * - 동일한 경로에 대해 여러 변경이 있을 수 있다. (ex. A: modify, B: delete)
     * - 변경 유형과 경로, 이전 경로가 모두 동일한 경우에만 중복으로 간주한다.
     */
    private fun unionParentsDiff(
        repositoryId: Long,
        commitHash: String,
        parentTrees: List<ObjectId>,
        newTreeId: ObjectId,
        repo: Repository,
        commitInstant: Instant
    ): IndexFailures {
        val seen = Collections.synchronizedSet(mutableSetOf<String>())
        val all = mutableListOf<DiffEntry>()
        parentTrees.forEach { pt ->
            val ds = computeDiff(repo, pt, newTreeId)
            ds.forEach { de ->
                val key = "${de.changeType}:${de.newPath}:${de.oldPath}"
                if (seen.add(key)) all += de
            }
        }
        log.debug { "[DiffScan] union merged=${all.size} parents=${parentTrees.size}" }
        val result = processDiffs(repositoryId, commitHash, repo, commitInstant, all)

        return result
    }

    /**
     * 두 트리의 차이를 계산한다.
     */
    private fun computeDiff(repo: Repository, oldTreeId: ObjectId, newTreeId: ObjectId): List<DiffEntry> {
        repo.newObjectReader().use { reader ->
            val oldIter = CanonicalTreeParser().apply { reset(reader, oldTreeId) }
            val newIter = CanonicalTreeParser().apply { reset(reader, newTreeId) }
            return DiffFormatter(DisabledOutputStream.INSTANCE).use { df ->
                df.setRepository(repo)
                df.isDetectRenames = true
                df.scan(oldIter, newIter).also {
                    log.debug { "[DiffScan] entries=${it.size}" }
                }
            }
        }
    }

    /**
     * 변경된 파일들을 병렬로 처리한다.
     */
    private fun processDiffs(
        repositoryId: Long,
        commitHash: String,
        repo: Repository,
        commitInstant: Instant,
        diffs: List<DiffEntry>
    ): IndexFailures {
        val failures = Collections.synchronizedList(mutableListOf<FileFailure>())
        val errors = AtomicInteger(0)
        val sem = Semaphore(MAX_CONCURRENCY)

        diffs.forEach { d ->
            if (d.changeType == DiffEntry.ChangeType.DELETE || d.newPath == DiffEntry.DEV_NULL) return@forEach

            val path = d.newPath
            val name = path.substringAfterLast('/')
            val oid = d.newId?.toObjectId() ?: return@forEach

            processor.ensureParentDirs(repositoryId, commitHash, path, commitInstant)

            exec.submit(sem) {
                try {
                    processor.processFile(repositoryId, commitHash, path, name, oid, repo, commitInstant)
                } catch (e: Exception) {
                    errors.incrementAndGet()
                    failures += FileFailure(path, oid.name, "FILE_PROCESS_FAILED", e)
                }
            }
        }

        exec.barrier(sem, MAX_CONCURRENCY)
        return IndexFailures(errors.get(), failures.toList())
    }
}
