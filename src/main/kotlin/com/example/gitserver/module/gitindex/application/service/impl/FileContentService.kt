package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.module.gitindex.infrastructure.dynamodb.BlobQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobStorageReader
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import org.springframework.stereotype.Service

@Service
class FileContentService(
    private val blobQueryRepository: BlobQueryRepository,
    private val treeQueryRepository: TreeQueryRepository,
    private val s3BlobStorageReader: S3BlobStorageReader
) {

    private val maxTextBytes: Long = 1_048_576

    fun getFileContent(
        repositoryId: Long,
        commitHash: String?,
        path: String,
        branch: String?
    ): FileContentResponse {
        val hash = requireNotNull(commitHash) { "commitHash는 필수입니다." }

        val normPath = path.trim().trim('/')

        // 커밋 메타 조회
        val commitItem = treeQueryRepository.getCommitItem(repositoryId, hash, branch)
            ?: treeQueryRepository.getCommitItem(repositoryId, hash, null)
            ?: throw IllegalStateException("커밋 정보가 없습니다: hash=$hash, branch=$branch")

        // 트리에서 파일 해시 조회
        val treeItem = treeQueryRepository.getTreeItem(repositoryId, hash, normPath)
            ?: throw NoSuchElementException("파일 트리 정보가 없습니다: $normPath")

        val fileHash = treeItem.fileHash
            ?: throw IllegalStateException("file_hash가 없습니다: $normPath")

        // 블롭 메타
        val blobMeta = blobQueryRepository.getBlobMeta(repositoryId, fileHash)
            ?: throw NoSuchElementException("Blob 메타정보가 없습니다: fileHash=$fileHash")

        // S3 키 안전 파싱
        val blobKey = blobMeta.externalStorageKey.let { key ->
            if (key.startsWith("blobs/")) key.removePrefix("blobs/") else key
        }

        // 콘텐츠 로드(텍스트만, 대용량 가드)
        val content: String? =
            if (!blobMeta.isBinary) {
                if (blobMeta.size != null && blobMeta.size > maxTextBytes) {
                    null
                } else {
                    s3BlobStorageReader.readBlobAsString(blobKey)
                }
            } else null

        val committer = commitItem["author_id"]?.n()?.toLongOrNull()?.let { userId ->
            RepositoryUserResponse(
                userId = userId,
                nickname = commitItem["author_name"]?.s() ?: "unknown",
                profileImageUrl = commitItem["author_profile_image_url"]?.s()
            )
        }

        return FileContentResponse(
            path = normPath,
            content = content,
            isBinary = blobMeta.isBinary,
            mimeType = blobMeta.mimeType,
            size = blobMeta.size,
            commitHash = hash,
            commitMessage = commitItem["message"]?.s(),
            committedAt = commitItem["committed_at"]?.s(),
            committer = committer
        )
    }
}
