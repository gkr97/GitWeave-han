package com.example.gitserver.module.gitindex.indexer.application.internal

import com.example.gitserver.module.gitindex.indexer.infrastructure.runtime.ExecutorGateway
import mu.KotlinLogging
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import java.time.Instant
import java.util.Collections
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

data class IndexFailures(val count: Int, val details: List<FileFailure>)
data class FileFailure(val path: String, val objectId: String, val reason: String, val throwable: Throwable?)

@Component
@Profile("gitindexer")
class TreeScanIndexer(
    private val processor: BlobProcessor,
    private val exec: ExecutorGateway
) {
    companion object { private const val MAX_CONCURRENCY = 20 }

    /**
     * 주어진 트리를 스캔하고 파일들을 처리합니다.
     * - 디렉토리는 재귀적으로 스캔
     * - 파일은 BlobProcessor를 통해 처리
     * - 심볼릭 링크와 서브모듈도 별도로 처리
     *
     * @param repositoryId 저장소 ID
     * @param commitHash 커밋 해시
     * @param treeId 스캔할 트리 ID
     * @param repo JGit Repository 객체
     * @param basePath 현재 경로 (재귀 호출 시 사용, 기본값은 빈 문자열)
     * @param commitInstant 커밋 시각
     * @return 처리 실패 정보(IndexFailures)
     */
    fun scan(
        repositoryId: Long,
        commitHash: String,
        treeId: ObjectId,
        repo: Repository,
        basePath: String = "",
        commitInstant: Instant,
    ): IndexFailures {
        val failures = Collections.synchronizedList(mutableListOf<FileFailure>())
        val errors = AtomicInteger(0)
        val sem = Semaphore(MAX_CONCURRENCY)

        log.debug { "[TreeScan] 시작 repoId=$repositoryId, commit=$commitHash, treeId=${treeId.name}, basePath=$basePath" }

        TreeWalk(repo).use { tw ->
            tw.addTree(treeId); tw.isRecursive = false
            while (tw.next()) {
                val name = tw.nameString
                val path = if (basePath.isEmpty()) name else "$basePath/$name"
                val objectId = tw.getObjectId(0)
                val mode: FileMode = tw.getFileMode(0)

                log.debug { "[TreeScan] 처리 중 repoId=$repositoryId, commit=$commitHash, path=$path, mode=${mode}, objectId=${objectId.name}" }

                when {
                    mode == FileMode.GITLINK -> {
                        try { processor.processGitlink(repositoryId, commitHash, path, name, objectId, commitInstant)
                        log.debug { "[TreeScan] GITLINK 처리 완료 repoId=$repositoryId, commit=$commitHash, path=$path" } }
                        catch (e: Exception) { errors.incrementAndGet(); failures += FileFailure(path, objectId.name, "GITLINK_SAVE_FAILED", e) }
                    }
                    processor.isSymlink(mode) -> {
                        try { processor.processSymlink(repositoryId, commitHash, path, name, objectId, commitInstant)
                        log.debug { "[TreeScan] SYMLINK 처리 완료 repoId=$repositoryId, commit=$commitHash, path=$path" } }
                        catch (e: Exception) { errors.incrementAndGet(); failures += FileFailure(path, objectId.name, "SYMLINK_SAVE_FAILED", e) }
                    }
                    tw.isSubtree -> {
                        try { processor.processDirectory(repositoryId, commitHash, path, name, objectId, commitInstant)
                        log.debug { "[TreeScan] DIRECTORY 처리 완료 repoId=$repositoryId, commit=$commitHash, path=$path" } }
                        catch (e: Exception) { errors.incrementAndGet(); failures += FileFailure(path, objectId.name, "DIRECTORY_SAVE_FAILED", e) }

                        val child = scan(repositoryId, commitHash, objectId, repo, path, commitInstant)
                        if (child.count > 0) errors.addAndGet(child.count)
                        if (child.details.isNotEmpty()) failures.addAll(child.details)
                    }
                    else -> {
                        exec.submit(sem) {
                            try {
                                processor.processFile(repositoryId, commitHash, path, name, objectId, repo, commitInstant)
                                log.debug { "[TreeScan] FILE 처리 완료 repoId=$repositoryId, commit=$commitHash, path=$path" }
                            } catch (e: Exception) {
                                errors.incrementAndGet()
                                failures += FileFailure(path, objectId.name, "FILE_PROCESS_FAILED", e)
                            }
                        }
                    }
                }
            }
        }

        exec.barrier(sem, MAX_CONCURRENCY)
        log.info { "[TreeScan] 인덱싱 완료 repoId=$repositoryId, commit=$commitHash, treeId=${treeId.name}, failures=${failures.size}, errors=${errors.get()}" }
        return IndexFailures(errors.get(), failures.toList())
    }
}
