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

private val log = KotlinLogging.logger {}

@Service
class BlobIndexerServiceImpl(
    private val gitIndexWriter: GitIndexWriter,
    private val blobUploader: S3BlobUploader,
    private val userRepository: UserRepository,
) : BlobIndexer {

    /**
     * Git 저장소를 인덱싱합니다.
     * @param repositoryId 인덱싱할 저장소 ID
     * @param workDir Git 저장소의 작업 디렉토리 경로
     */
    override fun indexRepository(repositoryId: Long, workDir: Path) {
        log.info { "[indexRepository] 시작 - repositoryId=$repositoryId, workDir=$workDir" }

        val git = try {
            Git.open(workDir.toFile())
        } catch (e: Exception) {
            throw GitRepositoryOpenException(workDir.toString(), e)
        }

        git.use {
            RevWalk(it.repository).use { revWalk ->
                val head = it.repository.resolve("HEAD") ?: throw GitHeadNotFoundException()
                val commit = try {
                    revWalk.parseCommit(head)
                } catch (e: Exception) {
                    throw GitCommitParseException(e)
                }

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
                gitIndexWriter.saveCommit(commitVo)
                log.info { "커밋 저장 완료 - hash=${commit.name}, author=${commit.authorIdent.name}" }

                walkTree(
                    repositoryId = repositoryId,
                    commitHash = commit.name,
                    treeId = tree.id,
                    repo = it.repository,
                    parentPath = "",
                    gitIndexWriter = gitIndexWriter,
                    blobUploader = blobUploader
                )

                log.info { "[indexRepository] 완료 - repositoryId=$repositoryId" }
            }
        }
    }

    /**
     * Git 이벤트를 처리하여 인덱싱합니다.
     * @param event Git 이벤트 정보
     * @param gitDir Git 저장소 디렉토리 경로
     */
    override fun indexPush(event: GitEvent, gitDir: Path) {
        log.info { "[indexPush] PUSH 이벤트 인덱싱 시작 - repoId=${event.repositoryId}, branch=${event.branch}, oldrev=${event.oldrev}, newrev=${event.newrev}" }
        val repoDir = gitDir.toFile()
        val git = try {
            Git.open(repoDir)
        } catch (e: Exception) {
            throw GitRepositoryOpenException(repoDir.path, e)
        }

        git.use {
            RevWalk(it.repository).use { revWalk ->
                val oldId = event.oldrev?.let { ObjectId.fromString(it) }
                val newId = event.newrev?.let { ObjectId.fromString(it) }
                val EMPTY_COMMIT = "0000000000000000000000000000000000000000"

                if (newId == null) {
                    log.warn { "[indexPush] newrev 없음" }
                    return
                }

                val commits = mutableListOf<RevCommit>()

                if (event.oldrev == null || event.oldrev == EMPTY_COMMIT) {
                    revWalk.markStart(revWalk.parseCommit(newId))
                    for (commit in revWalk) {
                        commits += commit
                    }
                } else {
                    revWalk.markStart(revWalk.parseCommit(newId))
                    revWalk.markUninteresting(revWalk.parseCommit(oldId))
                    for (commit in revWalk) {
                        commits += commit
                    }
                }
                log.info { "[indexPush] 인덱싱 대상 커밋 수: ${commits.size}" }
                for (commit in commits) {
                    indexSingleCommit(
                        event.repositoryId,
                        commit,
                        it,
                        event.branch ?: "main"
                    )
                }
            }
        }
    }

    /**
     * 단일 커밋을 인덱싱합니다.
     * @param repositoryId 레포지토리 ID
     * @param commit 인덱싱할 커밋
     * @param git Git 객체
     * @param branch 브랜치 이름
     */
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

        log.info { "[indexSingleCommit] authorEmail=$authorEmail, authorId=$authorId" }

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
        gitIndexWriter.saveCommit(commitVo)
        log.info { "[indexSingleCommit] 커밋 인덱싱 - hash=${commit.name}, author=${commit.authorIdent.name}" }

        // 3.. 재귀 트리 순회
        walkTree(
            repositoryId = repositoryId,
            commitHash = commit.name,
            treeId = tree.id,
            repo = git.repository,
            parentPath = "",
            gitIndexWriter = gitIndexWriter,
            blobUploader = blobUploader
        )

        log.info { "[indexSingleCommit] 완료 - commit=${commit.name}" }
    }

    /**
     * 트리를 순회하며 파일과 디렉토리를 인덱싱합니다.
     * @param repositoryId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @param treeId 트리 ID
     * @param repo Git 저장소 객체
     * @param parentPath 부모 디렉토리 경로
     * @param gitIndexWriter Git 인덱스 작성기
     * @param blobUploader S3 블롭 업로더
     */
    private fun walkTree(
        repositoryId: Long,
        commitHash: String,
        treeId: ObjectId,
        repo: Repository,
        parentPath: String,
        gitIndexWriter: GitIndexWriter,
        blobUploader: S3BlobUploader
    ) {
        val treeWalk = TreeWalk(repo).apply {
            addTree(treeId)
            isRecursive = false
        }

        val maxConcurrency = 20
        val semaphore = Semaphore(maxConcurrency)
        val threads = mutableListOf<Thread>()

        while (treeWalk.next()) {
            val name = treeWalk.nameString
            val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
            val objectId = treeWalk.getObjectId(0)

            log.info { "[walkTree] name=$name, path=$path, parentPath=$parentPath, isSubtree=${treeWalk.isSubtree}, objectId=$objectId" }

            if (treeWalk.isSubtree) {
                // 디렉토리 저장
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
                gitIndexWriter.saveTree(treeVo)
                log.info { "[walkTree:DIR] 디렉토리 저장 완료 - path=$path, depth=${treeVo.depth}" }

                // 재귀 호출
                walkTree(
                    repositoryId = repositoryId,
                    commitHash = commitHash,
                    treeId = objectId,
                    repo = repo,
                    parentPath = path,
                    gitIndexWriter = gitIndexWriter,
                    blobUploader = blobUploader
                )
            } else {
                semaphore.acquire()
                val thread = Thread.startVirtualThread {
                    try {
                        log.info { "[walkTree:FILE] 파일 저장 시작 - path=$path, depth=${path.count { it == '/' }}" }

                        val loader = repo.open(objectId)
                        val fileSize = loader.size
                        val hash = objectId.name
                        val extension = path.substringAfterLast('.', "")

                        // 1. 파일 요량 체크
                        if (fileSize > 100 * 1024 * 1024) {
                            log.info { "[walkTree:FILE] 대용량 파일 감지(${fileSize} bytes) - 스트리밍 업로드 진행, 라인 수.바이너리 판별 생략" }
                            loader.openStream().use { inputStream ->
                                blobUploader.uploadStream(hash, inputStream, fileSize)
                            }

                            val blobVo = Blob(
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
                            gitIndexWriter.saveBlob(blobVo)

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
                                lastModifiedAt = Instant.now()
                            )
                            gitIndexWriter.saveTree(treeVo)
                        } else {
                            val bytes = loader.bytes
                            val mimeType = bytes.detectMimeType()

                            blobUploader.upload(hash, bytes)

                            val blobVo = Blob(
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
                            gitIndexWriter.saveBlob(blobVo)

                            val treeVo = BlobTree(
                                repositoryId = repositoryId,
                                commitHash = CommitHash(commitHash),
                                path = FilePath(path),
                                name = name,
                                isDirectory = false,
                                fileHash = hash,
                                size = bytes.size.toLong(),
                                depth = path.count { it == '/' },
                                fileTypeCodeId = null,
                                lastModifiedAt = Instant.now()
                            )
                            gitIndexWriter.saveTree(treeVo)
                        }

                        log.info { "[walkTree:FILE] 파일 저장 완료 - path=$path" }
                    } catch (e: Exception) {
                        log.warn(e) { "[walkTree] 파일 처리 실패 - path=$path, objectId=$objectId" }
                    } finally {
                        semaphore.release()
                    }
                }
                threads += thread
            }
        }

        threads.forEach { it.join() }
    }

}
