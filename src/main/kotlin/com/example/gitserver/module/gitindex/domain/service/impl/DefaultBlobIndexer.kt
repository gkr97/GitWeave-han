package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.common.extension.*
import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.domain.service.BlobIndexer
import com.example.gitserver.module.gitindex.domain.service.GitIndexWriter
import com.example.gitserver.module.gitindex.domain.vo.*
import com.example.gitserver.module.gitindex.exception.*
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobUploader
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class DefaultBlobIndexer(
    private val gitIndexWriter: GitIndexWriter,
    private val blobUploader: S3BlobUploader,
    private val userRepository: UserRepository,
) : BlobIndexer {

    @Transactional
    override fun indexRepository(repositoryId: Long, workDir: Path) {
        log.info { "[indexRepository] 시작 - repositoryId=$repositoryId, workDir=$workDir" }

        val git = try {
            Git.open(workDir.toFile())
        } catch (e: Exception) {
            throw GitRepositoryOpenException(workDir.toString(), e)
        }

        git.use {
            RevWalk(it.repository).use { revWalk ->
                val head = it.repository.resolve("HEAD")
                    ?: throw GitHeadNotFoundException()

                val commit = try {
                    revWalk.parseCommit(head)
                } catch (e: Exception) {
                    throw GitCommitParseException(e)
                }

                val tree = commit.tree
                val fullBranch = it.repository.fullBranch ?: "refs/heads/main"
                val branch = fullBranch.substringAfterLast("/")

                val authorEmail = commit.authorIdent.emailAddress
                val authorId = userRepository.findByEmail(authorEmail)?.id
                    ?: throw AuthorNotFoundException(authorEmail)

                log.info { "커밋 파싱 완료 - hash=${commit.name}, author=${commit.authorIdent.name}" }

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

                val treeWalk = TreeWalk(it.repository).apply {
                    addTree(tree)
                    isRecursive = true
                }

                while (treeWalk.next()) {
                    val path = treeWalk.pathString
                    val objectId = treeWalk.getObjectId(0)
                    val bytes = try {
                        it.repository.open(objectId).bytes
                    } catch (e: Exception) {
                        log.warn(e) { "오브젝트 로드 실패 - path=$path, objectId=$objectId" }
                        continue
                    }

                    val hash = objectId.name
                    val extension = path.substringAfterLast('.', "")
                    val mimeType = bytes.detectMimeType()

                    try {
                        blobUploader.upload(repositoryId, hash, bytes)
                    } catch (e: Exception) {
                        throw S3UploadException(path, hash, e)
                    }

                    log.debug { "업로드 완료 - path=$path, hash=$hash, size=${bytes.size}" }

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
                        commitHash = CommitHash(commit.name),
                        path = FilePath(path),
                        name = path.substringAfterLast('/'),
                        isDirectory = treeWalk.isSubtree,
                        fileHash = hash,
                        size = bytes.size.toLong(),
                        depth = path.count { it == '/' },
                        fileTypeCodeId = null,
                        lastModifiedAt = Instant.now()
                    )
                    gitIndexWriter.saveTree(treeVo)
                }

                log.info { "[indexRepository] 완료 - repositoryId=$repositoryId" }
            }
        }
    }
}
