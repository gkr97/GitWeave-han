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
    fun getFileContent(
        repositoryId: Long,
        commitHash: String?,
        path: String,
        branch: String?
    ): FileContentResponse {
        val hash = commitHash ?: throw IllegalArgumentException("commitHash는 필수입니다.")

        val treeItem = treeQueryRepository.getTreeItem(repositoryId, hash, path)
            ?: throw IllegalArgumentException("파일 트리 정보가 없습니다: $path")

        val fileHash = treeItem.fileHash
            ?: throw IllegalArgumentException("file_hash가 없습니다: $path")

        val blobMeta = blobQueryRepository.getBlobMeta(repositoryId, fileHash)
            ?: throw IllegalArgumentException("Blob 메타정보가 없습니다: $fileHash")

        val commitItem = treeQueryRepository.getCommitItem(repositoryId, hash, branch)
            ?: throw IllegalArgumentException("커밋 정보가 없습니다: $hash, branch: $branch")

        val committer = commitItem["author_id"]?.n()?.toLongOrNull()?.let { userId ->
            RepositoryUserResponse(
                userId = userId,
                nickname = commitItem["author_name"]?.s() ?: "unknown",
                profileImageUrl = commitItem["author_profile_image_url"]?.s()
            )
        }

        val blobHash = blobMeta.externalStorageKey.removePrefix("blobs/")
        val content = if (!blobMeta.isBinary)
            s3BlobStorageReader.readBlobAsString(blobHash)
        else null

        return FileContentResponse(
            path = path,
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

