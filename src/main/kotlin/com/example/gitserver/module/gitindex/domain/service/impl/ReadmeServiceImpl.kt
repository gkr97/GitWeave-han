package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.infrastructure.dynamodb.ReadmeQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobStorageReader
import com.example.gitserver.module.gitindex.domain.service.ReadmeService
import com.example.gitserver.module.gitindex.exception.ReadmeLoadFailedException
import com.example.gitserver.module.gitindex.exception.ReadmeNotFoundException
import com.example.gitserver.module.gitindex.exception.ReadmeRenderException
import com.example.gitserver.module.repository.interfaces.dto.LanguageStatResponse
import com.example.gitserver.module.repository.interfaces.dto.ReadmeResponse
import mu.KotlinLogging
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReadmeServiceImpl(
    private val readmeQueryRepository: ReadmeQueryRepository,
    private val blobReader: S3BlobStorageReader
) : ReadmeService {

    private val log = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun getReadmeInfo(repoId: Long, commitHash: String): ReadmeResponse {
        val result = readmeQueryRepository.findReadmeBlobInfo(repoId, commitHash)
        return ReadmeResponse(
            exists = result != null,
            path = result?.first.orEmpty(),
            blobHash = result?.second
        )
    }

    @Transactional(readOnly = true)
    override fun getReadmeContent(repoId: Long, commitHash: String): String {
        val blobHash = getReadmeInfo(repoId, commitHash).blobHash
            ?: throw ReadmeNotFoundException(repoId, commitHash)

        return blobReader.readBlobAsString(repoId, blobHash)
            ?: throw ReadmeLoadFailedException("blobs/$repoId/$blobHash", Exception("null body"))
    }

    @Transactional(readOnly = true)
    override fun getReadmeHtml(repoId: Long, commitHash: String): String {
        val content = getReadmeContent(repoId, commitHash)
        return try {
            val parser = Parser.builder().build()
            val document = parser.parse(content)
            HtmlRenderer.builder().build().render(document)
        } catch (e: Exception) {
            log.warn(e) { "[getReadmeHtml] 마크다운 렌더링 실패" }
            throw ReadmeRenderException(e)
        }
    }

    @Transactional(readOnly = true)
    override fun getLanguageStats(repositoryId: Long): List<LanguageStatResponse> {
        val counts = readmeQueryRepository.countBlobsByExtension(repositoryId)
        val total = counts.values.sum().takeIf { it > 0 } ?: return emptyList()

        return counts.map { (ext, count) ->
            LanguageStatResponse(
                extension = ext,
                count = count,
                ratio = count.toFloat() / total
            )
        }
    }
}

