package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.common.extension.*
import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.application.service.BlobIndexer
import com.example.gitserver.module.gitindex.application.service.GitIndexWriter
import com.example.gitserver.module.gitindex.domain.vo.*
import com.example.gitserver.module.gitindex.exception.*
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobUploader
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

@Service
class BlobIndexerServiceImpl(
    private val gitIndexWriter: GitIndexWriter,
    private val blobUploader: S3BlobUploader,
    private val userRepository: UserRepository,
) : BlobIndexer {

    /**
     * 레포지토리의 HEAD 커밋을 인덱싱합니다.
     * @param repositoryId 레포지토리 ID
     * @param workDir 작업 디렉터리 경로
     * @throws GitRepositoryOpenException 레포지토리 열기 실패
     * @throws GitHeadNotFoundException HEAD 커밋을 찾을 수 없음
     * @throws GitCommitParseException 커밋 파싱 실패
     * @throws AuthorNotFoundException 작성자 정보가 없음
     * @throws IndexingFailedException 파일 인덱싱 실패
     */
    override fun indexRepository(repositoryId: Long, workDir: Path) {
        log.info { "[indexRepository] 시작 - repositoryId=$repositoryId, workDir=$workDir" }

        val git = try { Git.open(workDir.toFile()) }
        catch (e: Exception) { throw GitRepositoryOpenException(workDir.toString(), e) }

        git.use {
            RevWalk(it.repository).use { revWalk ->
                val head = it.repository.resolve("HEAD") ?: throw GitHeadNotFoundException()
                val commit = try { revWalk.parseCommit(head) }
                catch (e: Exception) { throw GitCommitParseException(e) }

                val tree = commit.tree
                val fullBranch = it.repository.fullBranch ?: "refs/heads/main"

                val authorEmail = commit.authorIdent.emailAddress
                val authorId = userRepository.findByEmailAndIsDeletedFalse(authorEmail)?.id
                    ?: throw AuthorNotFoundException(authorEmail)

                val commitVo = Commit(
                    repositoryId = repositoryId,
                    hash = CommitHash(commit.name),
                    message = commit.fullMessage,
                    authorName = commit.authorIdent.name,
                    authorId = authorId,
                    authorEmail = authorEmail,
                    committedAt = commit.authorIdent.whenAsInstant,
                    committerName = commit.committerIdent.name,
                    committerEmail = commit.committerIdent.emailAddress,
                    treeHash = TreeHash(tree.id.name),
                    parentHashes = commit.parents.map { it.name },
                    createdAt = Instant.now(),
                    branch = fullBranch,
                )

                // 파일/디렉터리 인덱싱
                val errors = walkTree(
                    repositoryId = repositoryId,
                    commitHash = commit.name,
                    treeId = tree.id,
                    repo = it.repository,
                    parentPath = "",
                    gitIndexWriter = gitIndexWriter,
                    blobUploader = blobUploader
                )

                if (errors == 0) {
                    gitIndexWriter.saveCommit(commitVo)
                    log.info { "[indexRepository] 커밋 저장 완료 - hash=${commit.name}" }
                } else {
                    log.warn { "[indexRepository] 파일 인덱싱 오류 ${errors}건 → 커밋 저장 건너뜀 - commit=${commit.name}" }
                    throw IndexingFailedException(repositoryId, "파일 인덱싱 실패 건수=$errors")
                }

                log.info { "[indexRepository] 완료 - repositoryId=$repositoryId" }
            }
        }
    }

    override fun indexPush(event: GitEvent, gitDir: Path) {
        log.info { "[indexPush] 시작 - repoId=${event.repositoryId}," +
                " branch=${event.branch}, oldrev=${event.oldrev}, newrev=${event.newrev}" }
        val repoDir = gitDir.toFile()
        val git = try { Git.open(repoDir) }
        catch (e: Exception) { throw GitRepositoryOpenException(repoDir.path, e) }

        git.use {
            RevWalk(it.repository).use { revWalk ->
                val oldId = event.oldrev?.let { ObjectId.fromString(it) }
                val newId = event.newrev?.let { ObjectId.fromString(it) }
                val EMPTY = "0000000000000000000000000000000000000000"

                if (newId == null) {
                    log.warn { "[indexPush] newrev 없음" }
                    return
                }

                val commits = mutableListOf<RevCommit>()
                if (event.oldrev == null || event.oldrev == EMPTY) {
                    revWalk.markStart(revWalk.parseCommit(newId))
                } else {
                    revWalk.markStart(revWalk.parseCommit(newId))
                    revWalk.markUninteresting(revWalk.parseCommit(oldId))
                }
                for (c in revWalk) commits += c

                log.info { "[indexPush] 인덱싱 대상 커밋 수: ${commits.size}" }
                for (c in commits) {
                    indexSingleCommit(event.repositoryId, c, it, event.branch ?: "main")
                }
            }
        }
    }

    private fun indexSingleCommit(
        repositoryId: Long,
        commit: RevCommit,
        git: Git,
        branch: String
    ) {
        val tree = commit.tree
        val authorEmail = commit.authorIdent.emailAddress
        val authorId = userRepository.findByEmailAndIsDeletedFalse(authorEmail)?.id ?: run {
            log.warn { "[indexSingleCommit] authorId를 찾을 수 없음 - email=$authorEmail" }
            -1L
        }

        val commitVo = Commit(
            repositoryId = repositoryId,
            hash = CommitHash(commit.name),
            message = commit.fullMessage,
            authorName = commit.authorIdent.name,
            authorId = authorId,
            authorEmail = authorEmail,
            committedAt = commit.authorIdent.whenAsInstant,
            committerName = commit.committerIdent.name,
            committerEmail = commit.committerIdent.emailAddress,
            treeHash = TreeHash(tree.id.name),
            parentHashes = commit.parents.map { it.name },
            createdAt = Instant.now(),
            branch = branch,
        )

        val errors = walkTree(
            repositoryId = repositoryId,
            commitHash = commit.name,
            treeId = tree.id,
            repo = git.repository,
            parentPath = "",
            gitIndexWriter = gitIndexWriter,
            blobUploader = blobUploader
        )

        if (errors == 0) {
            gitIndexWriter.saveCommit(commitVo)
            log.info { "[indexSingleCommit] 커밋 저장 완료 - hash=${commit.name}" }
        } else {
            log.warn { "[indexSingleCommit] 파일 인덱싱 오류 ${errors}건 → 커밋 저장 건너뜀 - commit=${commit.name}" }
            throw IndexingFailedException(repositoryId, "파일 인덱싱 실패 건수=$errors")
        }
    }

    /**
     * 트리를 순회하며 파일과 디렉토리를 인덱싱
     * 반환: 실패 건수
     */
    private fun walkTree(
        repositoryId: Long,
        commitHash: String,
        treeId: ObjectId,
        repo: Repository,
        parentPath: String,
        gitIndexWriter: GitIndexWriter,
        blobUploader: S3BlobUploader
    ): Int {
        val treeWalk = TreeWalk(repo).apply {
            addTree(treeId)
            isRecursive = false
        }

        val maxConcurrency = 20
        val semaphore = Semaphore(maxConcurrency)
        val threads = mutableListOf<Thread>()
        val errorCount = AtomicInteger(0)

        while (treeWalk.next()) {
            val name = treeWalk.nameString
            val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
            val objectId = treeWalk.getObjectId(0)

            log.info { "[walkTree] name=$name, path=$path, parentPath=$parentPath, isSubtree=${treeWalk.isSubtree}, objectId=$objectId" }

            if (treeWalk.isSubtree) {
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
                    lastModifiedAt = Instant.now()
                )
                try {
                    gitIndexWriter.saveTree(treeVo)
                } catch (e: Exception) {
                    log.warn(e) { "[walkTree:DIR] 디렉토리 저장 실패 - path=$path, objectId=$objectId" }
                    errorCount.incrementAndGet()
                }

                val childErrors = walkTree(
                    repositoryId = repositoryId,
                    commitHash = commitHash,
                    treeId = objectId,
                    repo = repo,
                    parentPath = path,
                    gitIndexWriter = gitIndexWriter,
                    blobUploader = blobUploader
                )
                if (childErrors > 0) errorCount.addAndGet(childErrors)
            } else {
                semaphore.acquire()
                val thread = Thread.startVirtualThread {
                    try {
                        val loader = repo.open(objectId)
                        val fileSize = loader.size
                        val hash = objectId.name
                        val extension = path.substringAfterLast('.', "")

                        // 1) S3 업로드 먼저
                        if (fileSize > 100 * 1024 * 1024) {
                            loader.openStream().use { inputStream ->
                                blobUploader.uploadStream(hash, inputStream, fileSize)
                            }
                        } else {
                            val bytes = loader.bytes
                            blobUploader.upload(hash, bytes)
                        }

                        // 2) 업로드 성공 후 VO 생성
                        val blobVo = if (fileSize > 100 * 1024 * 1024) {
                            Blob(
                                repositoryId = repositoryId,
                                hash = BlobHash(hash),
                                path = FilePath(path),
                                extension = extension,
                                mimeType = null,
                                isBinary = true,
                                fileSize = fileSize,
                                lineCount = null,
                                externalStorageKey = "blobs/$hash",
                                createdAt = Instant.now()
                            )
                        } else {
                            val bytes = repo.open(objectId).bytes
                            val mimeType = bytes.detectMimeType()
                            Blob(
                                repositoryId = repositoryId,
                                hash = BlobHash(hash),
                                path = FilePath(path),
                                extension = extension,
                                mimeType = mimeType,
                                isBinary = bytes.isBinaryFile(),
                                fileSize = bytes.size.toLong(),
                                lineCount = bytes.countLines(),
                                externalStorageKey = "blobs/$hash",
                                createdAt = Instant.now()
                            )
                        }

                        val treeVo = BlobTree(
                            repositoryId = repositoryId,
                            commitHash = CommitHash(commitHash),
                            path = FilePath(path),
                            name = name,
                            isDirectory = false,
                            fileHash = hash,
                            size = blobVo.fileSize,
                            depth = path.count { it == '/' },
                            fileTypeCodeId = null,
                            lastModifiedAt = Instant.now()
                        )

                        // 3) Blob+Tree 원자 저장 하기
                        gitIndexWriter.saveBlobAndTree(blobVo, treeVo)
                        log.info { "[walkTree:FILE] 파일 저장 완료(atomic) - path=$path" }
                    } catch (e: Exception) {
                        log.warn(e) { "[walkTree:FILE] 처리 실패 - path=$path, objectId=$objectId" }
                        errorCount.incrementAndGet()
                    } finally {
                        semaphore.release()
                    }
                }
                threads += thread
            }
        }

        threads.forEach { it.join() }
        return errorCount.get()
    }
}
