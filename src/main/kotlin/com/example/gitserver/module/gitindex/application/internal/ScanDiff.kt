package com.example.gitserver.module.gitindex.application.internal

import com.example.gitserver.module.gitindex.infrastructure.runtime.ExecutorGateway
import mu.KotlinLogging
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
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
    private val exec: ExecutorGateway
) {
    companion object { private const val MAX_CONCURRENCY = 20 }

    /**
     * 두 트리의 차이를 스캔하고 변경된 파일들을 처리합니다.
     * - 삭제된 파일은 무시
     * - 추가/수정된 파일은 BlobProcessor를 통해 처리
     *
     * @param repositoryId 저장소 ID
     * @param commitHash 커밋 해시
     * @param parentTreeId 부모 트리 ID (null일 경우 newTreeId 사용)
     * @param newTreeId 새로운 트리 ID
     * @param repo JGit Repository 객체
     * @param commitInstant 커밋 시각
     * @return 처리 실패 정보(IndexFailures)
     */
    fun scan(
        repositoryId: Long,
        commitHash: String,
        parentTreeId: ObjectId?,
        newTreeId: ObjectId,
        repo: Repository,
        commitInstant: Instant
    ): IndexFailures {
        log.debug { "[DiffScan] 시작 repoId=$repositoryId, commit=$commitHash, parentTreeId=${parentTreeId?.name}, newTreeId=${newTreeId.name}" }

        val reader = repo.newObjectReader()
        val newIter = CanonicalTreeParser().apply { reset(reader, newTreeId) }
        val oldIter = CanonicalTreeParser().apply { reset(reader, parentTreeId ?: newTreeId) }


        val diffs: List<DiffEntry> = DiffFormatter(DisabledOutputStream.INSTANCE).use { df ->
            df.setRepository(repo); df.isDetectRenames = true; df.scan(oldIter, newIter)
            .also { log.debug { "[DiffScan] diff entries count: ${it.size}" } }
        }

        val failures = Collections.synchronizedList(mutableListOf<FileFailure>())
        val errors = AtomicInteger(0)
        val sem = Semaphore(MAX_CONCURRENCY)

        diffs.forEach { d ->
            if (d.changeType == DiffEntry.ChangeType.DELETE) {
                // TODO: 삭제된 파일 처리 로직 스냅샷 방식이라 고민
            } else {
                val path = d.newPath
                val name = path.substringAfterLast('/')
                val oid = d.newId?.toObjectId() ?: return@forEach
                exec.submit(sem) {
                    try {
                        processor.processFile(repositoryId, commitHash, path, name, oid, repo, commitInstant)
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                        failures += FileFailure(path, oid.name, "FILE_PROCESS_FAILED", e)
                    }
                }
            }
        }

        exec.barrier(sem, MAX_CONCURRENCY)
        return IndexFailures(errors.get(), failures.toList())
    }
}
