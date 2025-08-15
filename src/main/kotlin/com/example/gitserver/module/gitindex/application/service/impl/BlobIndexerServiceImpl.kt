package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.common.extension.*
import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.application.service.BlobIndexer
import com.example.gitserver.module.gitindex.application.service.GitIndexWriter
import com.example.gitserver.module.gitindex.domain.event.IndexingFailurePublisher
import com.example.gitserver.module.gitindex.domain.vo.*
import com.example.gitserver.module.gitindex.exception.*
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobUploader
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

@Service
class BlobIndexerServiceImpl(
    private val gitIndexWriter: GitIndexWriter,
    private val blobUploader: S3BlobUploader,
    private val userRepository: UserRepository,
    private val failurePublisher: IndexingFailurePublisher,
) : BlobIndexer {

    private val LINECOUNT_HARD_LIMIT_BYTES: Long = 5L * 1024 * 1024
    private val SAMPLE_BYTES: Int = 64 * 1024
    private val MAX_CONCURRENCY: Int = 20
    private val MAX_RETRIES: Int = 3
    private val RETRY_BASE_DELAY: Duration = Duration.ofMillis(200)

    private val EMPTY_SHA = "0000000000000000000000000000000000000000"

    override fun indexRepository(repositoryId: Long, workDir: Path) {
        log.info { "[indexRepository] 시작 - repositoryId=$repositoryId, workDir=$workDir" }

        val git = try { Git.open(workDir.toFile()) }
        catch (e: Exception) { throw GitRepositoryOpenException(workDir.toString(), e) }

        git.use { g ->
            RevWalk(g.repository).use { revWalk ->
                val head = g.repository.resolve("HEAD") ?: throw GitHeadNotFoundException()
                val commit = try { revWalk.parseCommit(head) }
                catch (e: Exception) { throw GitCommitParseException(e) }

                val fullBranch = normalizeBranch(g.repository.fullBranch)
                val commitVo = buildCommitVo(repositoryId, commit, fullBranch)

                val (errors, failures) = walkTree(
                    repositoryId = repositoryId,
                    commitHash = commit.name,
                    treeId = commit.tree.id,
                    repo = g.repository,
                )

                if (errors == 0) {
                    gitIndexWriter.saveCommit(commitVo)
                    log.info { "[indexRepository] 커밋 저장 완료 - hash=${commit.name}" }
                } else {
                    gitIndexWriter.saveCommit(commitVo)
                    failures.forEach { failurePublisher.publishFileFailure(repositoryId, commit.name, it.path, it.objectId, it.reason, it.throwable) }
                    log.warn { "[indexRepository] 파일 인덱싱 부분 실패 ${errors}건 - commit=${commit.name}" }
                }
                log.info { "[indexRepository] 완료 - repositoryId=$repositoryId" }
            }
        }
    }

    override fun indexPush(event: GitEvent, gitDir: Path) {
        log.info { "[indexPush] 시작 - repoId=${event.repositoryId}, branch=${event.branch}, oldrev=${event.oldrev}, newrev=${event.newrev}" }
        val repoDir = gitDir.toFile()
        val git = try { Git.open(repoDir) } catch (e: Exception) { throw GitRepositoryOpenException(repoDir.path, e) }

        git.use { g ->
            val repo = g.repository
            val newId = event.newrev?.let { ObjectId.fromString(it) } ?: run {
                log.warn { "[indexPush] newrev 없음" }; return
            }
            val oldId = event.oldrev?.takeIf { it != EMPTY_SHA }?.let { ObjectId.fromString(it) }
            val branch = normalizeBranch(event.branch)

            val isBranchCreate = (event.oldrev == null || event.oldrev == EMPTY_SHA)

            if (isBranchCreate) {
                if (gitIndexWriter.existsCommit(event.repositoryId, newId.name)) {
                    saveBranchMappingOnly(event.repositoryId, newId, repo, branch)
                    log.info { "[indexPush] 신규 브랜치 매핑만 저장 - branch=$branch, commit=${newId.name}" }
                    return
                }

                val commits = collectUntilKnown(repo, newId) { knownHash ->
                    gitIndexWriter.existsCommit(event.repositoryId, knownHash)
                }
                log.info { "[indexPush] 신규 브랜치 초기 인덱싱 커밋 수=${commits.size}" }
                for (c in commits.asReversed()) {
                    indexSingleCommit(event.repositoryId, c, g, branch)
                }
                return
            }

            if (oldId == null) {
                log.warn { "[indexPush] oldrev 없음(비정상 케이스) - new=${newId.name}" }
                return
            }

            val commits = revListOldToNew(repo, oldId, newId)
            log.info { "[indexPush] 일반 push 인덱싱 커밋 수=${commits.size}" }
            for (c in commits) {
                if (gitIndexWriter.existsCommit(event.repositoryId, c.name)) {
                    log.debug { "[indexPush] 이미 인덱싱된 커밋 스킵 - ${c.name}" }
                    continue
                }
                indexSingleCommit(event.repositoryId, c, g, branch)
            }
        }
    }


    private fun indexSingleCommit(
        repositoryId: Long,
        commit: RevCommit,
        git: Git,
        branch: String,
    ) {
        val commitVo = buildCommitVo(repositoryId, commit, branch)

        val (errors, failures) = walkTree(
            repositoryId = repositoryId,
            commitHash = commit.name,
            treeId = commit.tree.id,
            repo = git.repository,
        )

        if (errors == 0) {
            gitIndexWriter.saveCommit(commitVo)
            log.info { "[indexSingleCommit] 커밋 저장 완료 - hash=${commit.name}" }
        } else {
            gitIndexWriter.saveCommit(commitVo)
            failures.forEach { failurePublisher.publishFileFailure(repositoryId, commit.name, it.path, it.objectId, it.reason, it.throwable) }
            log.warn { "[indexSingleCommit] 파일 인덱싱 부분 실패 ${errors}건 - commit=${commit.name}" }
        }
    }

    data class FileFailure(val path: String, val objectId: String, val reason: String, val throwable: Throwable?)

    /**
     * 트리를 순회하며 파일과 디렉토리를 인덱싱.
     * @return Pair(실패 건수, 실패 목록)
     */
    private fun walkTree(
        repositoryId: Long,
        commitHash: String,
        treeId: ObjectId,
        repo: Repository,
        parentPath: String = "",
    ): Pair<Int, List<FileFailure>> {
        val failures = Collections.synchronizedList(mutableListOf<FileFailure>())
        val errorCount = AtomicInteger(0)

        TreeWalk(repo).use { tw ->
            tw.addTree(treeId)
            tw.isRecursive = false

            val taskList = mutableListOf<Callable<Unit>>()

            while (tw.next()) {
                val name = tw.nameString
                val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
                val objectId = tw.getObjectId(0)
                val mode: FileMode = tw.getFileMode(0)

                log.debug { "[walkTree] name=$name, path=$path, mode=$mode, isSubtree=${tw.isSubtree}, objectId=$objectId" }

                when {
                    // 1) 심볼릭 링크 먼저 처리
                    isSymlink(mode) -> {
                        val treeVo = BlobTree(
                            repositoryId = repositoryId,
                            commitHash = CommitHash(commitHash),
                            path = FilePath(path),
                            name = name,
                            isDirectory = false,
                            fileHash = objectId.name,
                            size = 0L,
                            depth = path.count { it == '/' },
                            fileTypeCodeId = null,
                            lastModifiedAt = Instant.now(),
                        )
                        try {
                            gitIndexWriter.saveTree(treeVo)
                        } catch (e: Exception) {
                            log.warn(e) { "[walkTree:SYMLINK] 저장 실패 - path=$path, objectId=$objectId" }
                            errorCount.incrementAndGet()
                            failures += FileFailure(path, objectId.name, "SYMLINK_SAVE_FAILED", e)
                        }
                    }
                    // 2) 디렉터리 처리
                    tw.isSubtree -> {
                        val treeVo = BlobTree(
                            repositoryId = repositoryId,
                            commitHash = CommitHash(commitHash),
                            path = FilePath(path),
                            name = name,
                            isDirectory = true,
                            fileHash = objectId.name,
                            size = 0L,
                            depth = path.count { it == '/' },
                            fileTypeCodeId = null,
                            lastModifiedAt = Instant.now(),
                        )
                        try {
                            gitIndexWriter.saveTree(treeVo)
                        } catch (e: Exception) {
                            log.warn(e) { "[walkTree:DIR] 디렉토리 저장 실패 - path=$path, objectId=$objectId" }
                            errorCount.incrementAndGet()
                            failures += FileFailure(path, objectId.name, "DIRECTORY_SAVE_FAILED", e)
                        }

                        val (childErrors, childFailures) = walkTree(
                            repositoryId = repositoryId,
                            commitHash = commitHash,
                            treeId = objectId,
                            repo = repo,
                            parentPath = path,
                        )
                        if (childErrors > 0) errorCount.addAndGet(childErrors)
                        if (childFailures.isNotEmpty()) failures.addAll(childFailures)
                    }
                    // 3) 일반 파일: 동시 처리
                    else -> {
                        taskList += Callable {
                            try {
                                processBlob(
                                    repositoryId = repositoryId,
                                    commitHash = commitHash,
                                    path = path,
                                    name = name,
                                    objectId = objectId,
                                    repo = repo,
                                )
                            } catch (e: Exception) {
                                log.warn(e) { "[walkTree:FILE] 처리 실패 - path=$path, objectId=$objectId" }
                                errorCount.incrementAndGet()
                                failures += FileFailure(path, objectId.name, "FILE_PROCESS_FAILED", e)
                            }
                        }
                    }
                }
            }

            if (taskList.isNotEmpty()) {
                val sem = Semaphore(MAX_CONCURRENCY)
                Executors.newVirtualThreadPerTaskExecutor().use { vexec ->
                    val futures = taskList.map { task ->
                        vexec.submit<Unit> {
                            sem.acquire()
                            try { task.call() } finally { sem.release() }
                        }
                    }
                    futures.forEach { f ->
                        try { f.get() } catch (e: Exception) {
                            log.debug(e) { "[walkTree] future get 예외" }
                        }
                    }
                }
            }
        }
        return errorCount.get() to failures
    }

    private fun processBlob(
        repositoryId: Long,
        commitHash: String,
        path: String,
        name: String,
        objectId: ObjectId,
        repo: Repository,
    ) {
        val hash = objectId.name

        val loader = repo.open(objectId)
        val fileSize = loader.size

        retry(maxRetries = MAX_RETRIES, baseDelay = RETRY_BASE_DELAY) {
            loader.openStream().use { inputStream ->
                blobUploader.uploadStream(hash, inputStream, fileSize)
            }
        }

        // 메타데이터 추출
        val (mimeType, isBinary, lineCount) = repo.open(objectId).openStream().use { ins ->
            analyzeStream(ins, fileSize)
        }

        val blobVo = Blob(
            repositoryId = repositoryId,
            hash = BlobHash(hash),
            path = FilePath(path),
            extension = path.substringAfterLast('.', ""),
            mimeType = mimeType,
            isBinary = isBinary,
            fileSize = fileSize,
            lineCount = lineCount,
            externalStorageKey = "blobs/$hash",
            createdAt = Instant.now(),
        )

        val treeVo = BlobTree(
            repositoryId = repositoryId,
            commitHash = CommitHash(commitHash),
            path = FilePath(path),
            name = name,
            isDirectory = false,
            fileHash = hash,
            size = fileSize,
            depth = path.count { it == '/' },
            fileTypeCodeId = null,
            lastModifiedAt = Instant.now(),
        )

        retry(maxRetries = MAX_RETRIES, baseDelay = RETRY_BASE_DELAY) {
            gitIndexWriter.saveBlobAndTree(blobVo, treeVo)
        }
        log.debug { "[processBlob] 저장 완료 - path=$path, hash=$hash, size=$fileSize" }
    }

    private fun analyzeStream(ins: InputStream, fileSize: Long): Triple<String?, Boolean, Int?> {
        val sample = ins.readNBytes(SAMPLE_BYTES)
        val mime: String = sample.detectMimeType()
        val isBinary: Boolean = sample.isBinaryFile()
        val lineCount: Int? = if (!isBinary && fileSize <= LINECOUNT_HARD_LIMIT_BYTES) sample.countLines() else null
        return Triple(mime, isBinary, lineCount)
    }

    private fun isSymlink(mode: FileMode): Boolean = mode == FileMode.SYMLINK

    private fun buildCommitVo(repositoryId: Long, commit: RevCommit, branch: String): Commit {
        val authorEmail = commit.authorIdent.emailAddress
        val authorId = userRepository.findByEmailAndIsDeletedFalse(authorEmail)?.id ?: -1L

        return Commit(
            repositoryId = repositoryId,
            hash = CommitHash(commit.name),
            message = commit.fullMessage,
            authorName = commit.authorIdent.name,
            authorId = authorId,
            authorEmail = authorEmail,
            committedAt = commit.authorIdent.whenAsInstant,
            committerName = commit.committerIdent.name,
            committerEmail = commit.committerIdent.emailAddress,
            treeHash = TreeHash(commit.tree.id.name),
            parentHashes = commit.parents.map { it.name },
            createdAt = Instant.now(),
            branch = branch,
        )
    }

    private fun normalizeBranch(input: String?): String {
        if (input.isNullOrBlank()) return "refs/heads/main"
        return if (input.startsWith("refs/heads/")) input else "refs/heads/$input"
    }

    private inline fun <T> retry(
        maxRetries: Int,
        baseDelay: Duration,
        block: () -> T,
    ): T {
        var attempt = 0
        var lastErr: Throwable? = null
        while (attempt < maxRetries) {
            try { return block() } catch (e: Throwable) {
                lastErr = e
                val delay = baseDelay.multipliedBy(1L shl attempt)
                log.warn(e) { "[retry] 실패 ${attempt + 1}/$maxRetries, ${delay.toMillis()}ms 후 재시도" }
                Thread.sleep(delay.toMillis())
                attempt++
            }
        }
        throw lastErr ?: IllegalStateException("retry 실패")
    }

    private fun saveBranchMappingOnly(
        repositoryId: Long,
        commitId: ObjectId,
        repo: Repository,
        branch: String
    ) {
        RevWalk(repo).use { rw ->
            val rc = rw.parseCommit(commitId)
            val vo = buildCommitVo(repositoryId, rc, branch)
            gitIndexWriter.saveCommit(vo)
        }
    }

    private fun collectUntilKnown(
        repo: Repository,
        start: ObjectId,
        isKnown: (String) -> Boolean
    ): List<RevCommit> {
        val acc = mutableListOf<RevCommit>()
        RevWalk(repo).use { rw ->
            rw.sort(RevSort.TOPO, true)
            rw.markStart(rw.parseCommit(start))
            for (c in rw) {
                if (isKnown(c.name)) break
                acc += c
            }
        }
        return acc
    }

    private fun revListOldToNew(repo: Repository, oldId: ObjectId, newId: ObjectId): List<RevCommit> {
        RevWalk(repo).use { rw ->
            rw.sort(RevSort.TOPO, true)
            rw.sort(RevSort.REVERSE, true)
            rw.markStart(rw.parseCommit(newId))
            rw.markUninteresting(rw.parseCommit(oldId))
            return rw.toList()
        }
    }

}
