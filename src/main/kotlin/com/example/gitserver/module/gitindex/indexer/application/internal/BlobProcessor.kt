package com.example.gitserver.module.gitindex.indexer.application.internal

import com.example.gitserver.module.gitindex.shared.domain.policy.BlobMetaAnalyzer
import com.example.gitserver.module.gitindex.indexer.infrastructure.runtime.RetryPolicy.retry
import com.example.gitserver.module.gitindex.shared.domain.Blob
import com.example.gitserver.module.gitindex.shared.domain.port.BlobObjectStorage
import com.example.gitserver.module.gitindex.shared.domain.port.IndexTxRepository
import com.example.gitserver.module.gitindex.shared.domain.vo.BlobHash
import com.example.gitserver.module.gitindex.shared.domain.vo.FilePath
import mu.KotlinLogging
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import java.io.ByteArrayInputStream
import java.time.Duration.ofMillis
import java.time.Instant

private val log = KotlinLogging.logger {}

@Component
@Profile("gitindexer")
class BlobProcessor(
    private val blobStorage: BlobObjectStorage,
    private val analyzer: BlobMetaAnalyzer,
    private val txRepo: IndexTxRepository,
) {
    companion object {
        private const val SAMPLE_BYTES: Int = 64 * 1024
        private const val MAX_RETRIES: Int = 3
        private val RETRY_BASE_DELAY = ofMillis(200)
        private const val SMALL_LIMIT: Long = 5L * 1024 * 1024
    }

    /**
     * 블롭(파일) 업서트 처리
     */
    fun processFile(
        repositoryId: Long,
        commitHash: String,
        path: String,
        name: String,
        objectId: ObjectId,
        repo: Repository,
        commitInstant: Instant
    ) {
        val hash = objectId.name
        val loader = repo.open(objectId)
        val size = loader.size

        if (size <= SMALL_LIMIT) {
            val bytes = loader.getCachedBytes(Int.MAX_VALUE)
            retry(MAX_RETRIES, RETRY_BASE_DELAY) {
                ByteArrayInputStream(bytes).use { blobStorage.putStream(hash, it, size) }
            }
            val (mime, bin, lines) = analyzer.analyzeSmall(bytes)
            saveBlobOnly(repositoryId, commitHash, path, name, hash, size, mime, bin, lines, commitInstant)
            log.debug { "[BlobProcessor] small uploaded path=$path size=$size" }
        } else {
            retry(MAX_RETRIES, RETRY_BASE_DELAY) {
                loader.openStream().use { blobStorage.putStream(hash, it, size) }
            }
            val (mime, bin, _) = repo.open(objectId).openStream().use {
                analyzer.analyzeLarge(it, size, SAMPLE_BYTES)
            }
            saveBlobOnly(repositoryId, commitHash, path, name, hash, size, mime, bin, null, commitInstant)
            log.debug { "[BlobProcessor] large uploaded path=$path size=$size" }
        }
    }

    /**
     * 심볼릭 링크는 TREE 기록을 하지 않으므로 no-op
     */
    fun processSymlink(
        repositoryId: Long, commitHash: String, path: String, name: String,
        objectId: ObjectId, commitInstant: Instant
    ) {
        log.trace { "[BlobProcessor:NOOP] symlink $path (${objectId.name})" }
    }

    /**
     * 서브모듈(gitlink)도 TREE 기록을 하지 않으므로 no-op
     */
    fun processGitlink(
        repositoryId: Long, commitHash: String, path: String, name: String,
        objectId: ObjectId, commitInstant: Instant
    ) {
        log.trace { "[BlobProcessor:NOOP] gitlink $path (${objectId.name})" }
    }

    /**
     * 디렉터리(TREE) 업서트는 중단. no-op
     */
    fun processDirectory(
        repositoryId: Long, commitHash: String, path: String, name: String,
        objectId: ObjectId, commitInstant: Instant
    ) {
        log.trace { "[BlobProcessor:NOOP] dir $path (${objectId.name})" }
    }

    /**
     * 부모 디렉터리 보정도 중단. no-op
     */
    fun ensureParentDirs(repositoryId: Long, commitHash: String, fullPath: String, commitInstant: Instant) {
        log.trace { "[BlobProcessor:NOOP] ensureParentDirs $fullPath" }
    }

    private fun saveBlobOnly(
        repositoryId: Long, commitHash: String, path: String, name: String,
        hash: String, size: Long, mime: String?, bin: Boolean, lines: Int?, commitInstant: Instant
    ) {
        val blob = Blob(
            repositoryId = repositoryId,
            hash = BlobHash(hash),
            path = FilePath(path),
            extension = path.substringAfterLast('.', ""),
            mimeType = mime,
            isBinary = bin,
            fileSize = size,
            lineCount = lines,
            externalStorageKey = "blobs/$hash",
            createdAt = commitInstant
        )
        txRepo.saveBlobOnly(blob)
    }

    fun isSymlink(mode: FileMode) = mode == FileMode.SYMLINK
}
