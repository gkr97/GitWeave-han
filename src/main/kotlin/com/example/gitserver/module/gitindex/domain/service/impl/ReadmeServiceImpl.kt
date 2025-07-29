package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.infrastructure.dynamodb.ReadmeQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobStorageReader
import com.example.gitserver.module.gitindex.domain.service.ReadmeService
import com.example.gitserver.module.gitindex.exception.ReadmeLoadFailedException
import com.example.gitserver.module.gitindex.exception.ReadmeNotFoundException
import com.example.gitserver.module.gitindex.exception.ReadmeRenderException
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.BlobQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.LanguageStatResponse
import com.example.gitserver.module.repository.interfaces.dto.ReadmeResponse
import mu.KotlinLogging
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReadmeServiceImpl(
    private val blobQueryRepository: BlobQueryRepository,
    private val readmeQueryRepository: ReadmeQueryRepository,
    private val blobReader: S3BlobStorageReader
) : ReadmeService {

    private val log = KotlinLogging.logger {}

    /**
     * README 파일의 존재 여부와 경로, blob 해시를 반환합니다.
     * @param repoId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @return ReadmeResponse 객체
     */
    @Transactional(readOnly = true)
    override fun getReadmeInfo(repoId: Long, commitHash: String): ReadmeResponse {
        val result = readmeQueryRepository.findReadmeBlobInfo(repoId, commitHash)
        return ReadmeResponse(
            exists = result != null,
            path = result?.first.orEmpty(),
            blobHash = result?.second
        )
    }

    /**
     * README 파일의 내용을 반환합니다.
     * @param repoId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @return README 파일의 내용
     * @throws ReadmeNotFoundException README 파일이 존재하지 않을 경우
     * @throws ReadmeLoadFailedException S3에서 블롭을 읽는 데 실패한 경우
     */
    @Transactional(readOnly = true)
    override fun getReadmeContent(repoId: Long, commitHash: String): String {
        val blobHash = getReadmeInfo(repoId, commitHash).blobHash
            ?: throw ReadmeNotFoundException(repoId, commitHash)

        return blobReader.readBlobAsString(blobHash)
            ?: throw ReadmeLoadFailedException("blobs/$repoId/$blobHash", Exception("null body"))
    }

    /**
     * README 파일의 HTML 렌더링 결과를 반환합니다.
     * @param repoId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @return HTML로 렌더링된 README 내용
     * @throws ReadmeRenderException 마크다운 렌더링 중 오류가 발생한 경우
     */
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

    /**
     * 레포지토리의 언어 통계 정보를 반환합니다.
     * @param repositoryId 레포지토리 ID
     * @return 언어 통계 리스트
     */
    @Transactional(readOnly = true)
    override fun getLanguageStats(repositoryId: Long): List<LanguageStatResponse> {
        val counts = blobQueryRepository.countBlobsByExtension(repositoryId)
        val total = counts.values.sum().takeIf { it > 0 } ?: return emptyList()

        val extToLang = mapOf(
            "kt" to "Kotlin",
            "java" to "Java",
            "md" to "Markdown",
            "sh" to "Shell",
            "py" to "Python"
        )

        return counts.mapNotNull { (ext, count) ->
            val lang = extToLang[ext] ?: return@mapNotNull null
            LanguageStatResponse(
                extension = ext,
                language = lang,
                count = count,
                ratio = count.toFloat() / total
            )
        }.sortedByDescending { it.ratio }
    }

}

