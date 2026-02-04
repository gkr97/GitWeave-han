package com.example.gitserver.module.gitindex.indexer.application.query

import com.example.gitserver.module.gitindex.shared.domain.port.BlobObjectStorage
import com.example.gitserver.module.gitindex.storage.infrastructure.git.GitPathResolver
import com.example.gitserver.common.cache.RequestCache
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Service
import com.example.gitserver.common.util.PathSecurityUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration.ofMinutes
import java.time.ZoneId
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

@Service
class FileContentQueryService(
    private val repositoryRepository: RepositoryRepository,
    private val gitPathResolver: GitPathResolver,
    private val blobStorage: BlobObjectStorage,
    private val requestCache: RequestCache,
) {

    companion object {
        const val MAX_TEXT_FILE_SIZE = 1_048_576L // 1MB
    }

    fun getFileContent(
        repositoryId: Long,
        commitHash: String?,
        path: String,
        branch: String?
    ): FileContentResponse {
        require(!commitHash.isNullOrBlank()) { "commitHash는 필수입니다." }
        val hash = commitHash!!

        val normPath = PathSecurityUtils.sanitizePath(path)

        log.info { "[FileContent] repo=$repositoryId commit=$hash path(raw)=$path path(norm)=$normPath branch=$branch" }

        val repoEntity = runCatching { requestCache.getRepo(repositoryId) }.getOrNull()
            ?: repositoryRepository.findByIdWithOwner(repositoryId)
                ?.also { runCatching { requestCache.putRepo(it) } }
            ?: throw RepositoryNotFoundException(repositoryId)

        val bareDir = gitPathResolver.bareDir(repoEntity.owner.id, repoEntity.name)
        var result: FileContentResponse? = null

        val took = measureTimeMillis {
            FileRepositoryBuilder()
                .setGitDir(File(bareDir))
                .setMustExist(true)
                .build()
                .use { repo ->
                    val (commit, tree) =
                        RevWalk(repo).use { rw ->
                            val oid = ObjectId.fromString(hash)
                            val c = rw.parseCommit(oid)
                            c to rw.parseTree(c.tree.id)
                        }

                    val blobOid = findBlob(repo, tree, normPath)
                        ?: throw com.example.gitserver.common.exception.EntityNotFoundException("파일", normPath)

                    val loader = repo.open(blobOid)
                    val size = loader.size

                    val smallBytes: ByteArray? = if (size <= MAX_TEXT_FILE_SIZE) {
                        loader.getCachedBytes(Int.MAX_VALUE)
                    } else null

                    val isBinary = when {
                        size > MAX_TEXT_FILE_SIZE -> true
                        smallBytes == null        -> true
                        else                      -> isBinaryProbe(smallBytes)
                    }

                    val mimeType = sniffMime(normPath, isBinary)

                    val (content, downloadUrl, expiresAt) =
                        if (!isBinary && smallBytes != null) {
                            Triple(String(smallBytes, StandardCharsets.UTF_8), null, null)
                        } else {
                            val key = "blobs/${blobOid.name()}"
                            val (url, exp) = blobStorage.presignForDownload(
                                key, normPath, mimeType, ofMinutes(10)
                            )
                            Triple(null, url, exp)
                        }

                    val committedAtStr = commit.committerIdent.whenAsInstant
                        .atZone(ZoneId.systemDefault()).toInstant().toString()

                    val committer = RepositoryUserResponse(
                        userId = -1L,
                        nickname = commit.committerIdent.name ?: "unknown",
                        profileImageUrl = null
                    )

                    result = FileContentResponse(
                        path = normPath,
                        content = content,
                        isBinary = isBinary,
                        mimeType = mimeType,
                        size = size,
                        commitHash = hash,
                        commitMessage = commit.fullMessage,
                        committedAt = committedAtStr,
                        committer = committer,
                        downloadUrl = downloadUrl,
                        expiresAt = expiresAt
                    )
                }
        }
        log.info { "[FileContent] done path=$normPath elapsed=${took}ms" }

        return requireNotNull(result)
    }

    private fun findBlob(repo: Repository, tree: RevTree, path: String): ObjectId? {
        TreeWalk(repo).use { tw ->
            tw.addTree(tree)
            tw.isRecursive = true
            while (tw.next()) {
                if (!tw.isSubtree && tw.pathString == path) return tw.getObjectId(0)
            }
        }
        return null
    }

    private fun isBinaryProbe(bytes: ByteArray): Boolean {
        val probeLen = minOf(8000, bytes.size)
        for (i in 0 until probeLen) if (bytes[i] == 0.toByte()) return true
        return false
    }

    private fun sniffMime(path: String, bin: Boolean): String? {
        if (!bin) return "text/plain"
        return when (path.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
