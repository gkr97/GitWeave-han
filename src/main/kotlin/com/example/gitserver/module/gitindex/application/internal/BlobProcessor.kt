package com.example.gitserver.module.gitindex.application.internal

import com.example.gitserver.module.gitindex.domain.policy.BlobMetaAnalyzer
import com.example.gitserver.module.gitindex.infrastructure.runtime.RetryPolicy.retry
import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.port.BlobObjectStorage
import com.example.gitserver.module.gitindex.domain.port.IndexTxRepository
import com.example.gitserver.module.gitindex.domain.port.TreeRepository
import com.example.gitserver.module.gitindex.domain.vo.*
import mu.KotlinLogging
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.time.Duration.ofMillis
import java.time.Instant
private val log = KotlinLogging.logger {}

@Component
class BlobProcessor(
    private val blobStorage: BlobObjectStorage,
    private val analyzer: BlobMetaAnalyzer,
    private val treeRepo: TreeRepository,
    private val txRepo: IndexTxRepository,
) {
    companion object {
        private const val SAMPLE_BYTES: Int = 64 * 1024
        private const val MAX_RETRIES: Int = 3
        private val RETRY_BASE_DELAY = ofMillis(200)
        private const val SMALL_LIMIT: Long = 5L * 1024 * 1024
    }

    /**
     * 파일(Blob) 객체를 처리합니다.
     * - 크기가 SMALL_LIMIT 이하인 경우 메모리로 읽어와 BlobMetaAnalyzer로 분석 후 저장
     * - 크기가 SMALL_LIMIT 초과인 경우 스트림으로 읽어와 BlobMetaAnalyzer로 샘플링 분석 후 저장
     * - Blob 데이터를 외부 스토리지에 업로드
     * - Blob 및 BlobTree 정보를 데이터베이스에 저장
     *
     * @param repositoryId 저장소 ID
     * @param commitHash 커밋 해시
     * @param path 파일 경로
     * @param name 파일 이름
     * @param objectId JGit ObjectId (Blob ID)
     * @param repo JGit Repository 객체
     * @param commitInstant 커밋 시각
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
            save(repositoryId, commitHash, path, name, hash, size, mime, bin, lines, commitInstant)
            log.debug { "[BlobProcessor] small uploaded path=$path size=$size" }
        } else {
            retry(MAX_RETRIES, RETRY_BASE_DELAY) {
                loader.openStream().use { blobStorage.putStream(hash, it, size) }
            }
            val (mime, bin, _) = repo.open(objectId).openStream().use {
                analyzer.analyzeLarge(it, size, SAMPLE_BYTES)
            }
            save(repositoryId, commitHash, path, name, hash, size, mime, bin, null, commitInstant)
            log.debug { "[BlobProcessor] large uploaded path=$path size=$size" }
        }
    }

    fun processSymlink(
        repositoryId: Long, commitHash: String, path: String, name: String,
        objectId: ObjectId, commitInstant: Instant
    ) {
        val tree = treeVo(repositoryId, commitHash, path, name, objectId.name, false, 0L, commitInstant)
        treeRepo.save(tree)
    }

    fun processGitlink(
        repositoryId: Long, commitHash: String, path: String, name: String,
        objectId: ObjectId, commitInstant: Instant
    ) {
        val tree = treeVo(repositoryId, commitHash, path, name, objectId.name, false, 0L, commitInstant)
        treeRepo.save(tree)
    }

    fun processDirectory(
        repositoryId: Long, commitHash: String, path: String, name: String,
        objectId: ObjectId, commitInstant: Instant
    ) {
        val tree = treeVo(repositoryId, commitHash, path, name, objectId.name, true, 0L, commitInstant)
        treeRepo.save(tree)
    }

    private fun save(
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
        val tree = treeVo(repositoryId, commitHash, path, name, hash, false, size, commitInstant)
        txRepo.saveBlobAndTree(blob, tree)
    }


    private fun treeVo(
        repositoryId: Long, commitHash: String, path: String, name: String,
        fileHash: String, isDir: Boolean, size: Long, commitInstant: Instant
    ) = BlobTree(
        repositoryId = repositoryId,
        commitHash = CommitHash(commitHash),
        path = FilePath(path),
        name = name,
        isDirectory = isDir,
        fileHash = fileHash,
        size = size,
        depth = path.count { it == '/' },
        fileTypeCodeId = null,
        lastModifiedAt = commitInstant
    )

    fun ensureParentDirs(repositoryId: Long, commitHash: String, fullPath: String, commitInstant: Instant) {
        val segments = fullPath.split('/').dropLast(1)
        if (segments.isEmpty()) return
        var acc = mutableListOf<String>()
        for (seg in segments) {
            acc += seg
            val dirPath = acc.joinToString("/")
            val tree = BlobTree(
                repositoryId = repositoryId,
                commitHash = CommitHash(commitHash),
                path = FilePath(dirPath),
                name = seg,
                isDirectory = true,
                fileHash = null,
                size = 0L,
                depth = dirPath.count { it == '/' },
                fileTypeCodeId = null,
                lastModifiedAt = commitInstant
            )
            treeRepo.save(tree)
        }
    }

    fun isSymlink(mode: FileMode) = mode == FileMode.SYMLINK
}
