package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.domain.service.impl.ReadmeServiceImpl
import com.example.gitserver.module.gitindex.exception.ReadmeLoadFailedException
import com.example.gitserver.module.gitindex.exception.ReadmeNotFoundException
import com.example.gitserver.module.gitindex.exception.ReadmeRenderException
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.BlobQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.ReadmeQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobStorageReader
import com.example.gitserver.module.repository.interfaces.dto.LanguageStatResponse
import com.example.gitserver.module.repository.interfaces.dto.ReadmeResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class ReadmeServiceImplTest {

    private lateinit var blobQueryRepository: BlobQueryRepository
    private lateinit var readmeQueryRepository: ReadmeQueryRepository
    private lateinit var blobReader: S3BlobStorageReader
    private lateinit var service: ReadmeServiceImpl

    @BeforeEach
    fun setUp() {
        blobQueryRepository = mock()
        readmeQueryRepository = mock()
        blobReader = mock()
        service = ReadmeServiceImpl(blobQueryRepository, readmeQueryRepository, blobReader)
    }

    @Test
    fun `getReadmeInfo - readme 존재할 때`() {
        whenever(readmeQueryRepository.findReadmeBlobInfo(1L, "abc123")).thenReturn(Pair("README.md", "blobhash"))
        val result = service.getReadmeInfo(1L, "abc123")
        assertEquals(ReadmeResponse(true, "README.md", "blobhash"), result)
    }

    @Test
    fun `getReadmeInfo - readme 없을 때`() {
        whenever(readmeQueryRepository.findReadmeBlobInfo(1L, "abc123")).thenReturn(null)
        val result = service.getReadmeInfo(1L, "abc123")
        assertEquals(ReadmeResponse(false, "", null), result)
    }

    @Test
    fun `getReadmeContent 정상 반환`() {
        whenever(readmeQueryRepository.findReadmeBlobInfo(1L, "abc123")).thenReturn(Pair("README.md", "blobhash"))
        whenever(blobReader.readBlobAsString(1L, "blobhash")).thenReturn("# Hello world")
        val content = service.getReadmeContent(1L, "abc123")
        assertEquals("# Hello world", content)
    }

    @Test
    fun `getReadmeContent - blobHash 없으면 예외`() {
        whenever(readmeQueryRepository.findReadmeBlobInfo(1L, "abc123")).thenReturn(null)
        assertThrows(ReadmeNotFoundException::class.java) {
            service.getReadmeContent(1L, "abc123")
        }
    }

    @Test
    fun `getReadmeContent - blobReader가 null이면 예외`() {
        whenever(readmeQueryRepository.findReadmeBlobInfo(1L, "abc123")).thenReturn(Pair("README.md", "blobhash"))
        whenever(blobReader.readBlobAsString(1L, "blobhash")).thenReturn(null)
        assertThrows(ReadmeLoadFailedException::class.java) {
            service.getReadmeContent(1L, "abc123")
        }
    }

    @Test
    fun `getReadmeHtml 정상 반환`() {
        whenever(readmeQueryRepository.findReadmeBlobInfo(1L, "abc123")).thenReturn(Pair("README.md", "blobhash"))
        whenever(blobReader.readBlobAsString(1L, "blobhash")).thenReturn("# Hello")
        val html = service.getReadmeHtml(1L, "abc123")
        assertTrue(html.contains("<h1>Hello</h1>") || html.contains("<h1>Hello</h1>\n")) // 환경에 따라 라인피드 포함 가능
    }

    @Test
    fun `getLanguageStats 정상 동작`() {
        val counts = mapOf("kt" to 5, "java" to 3, "md" to 2)
        whenever(blobQueryRepository.countBlobsByExtension(1L)).thenReturn(counts)
        val result = service.getLanguageStats(1L)
        assertEquals(3, result.size)
        assertEquals("Kotlin", result[0].language)
        assertEquals(0.5f, result[0].ratio) // 5/10
    }

    @Test
    fun `getLanguageStats 총합 0이면 빈 리스트`() {
        val counts = mapOf<String, Int>()
        whenever(blobQueryRepository.countBlobsByExtension(1L)).thenReturn(counts)
        val result = service.getLanguageStats(1L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLanguageStats 알 수 없는 확장자는 제외`() {
        val counts = mapOf("kt" to 1, "foo" to 3)
        whenever(blobQueryRepository.countBlobsByExtension(1L)).thenReturn(counts)
        val result = service.getLanguageStats(1L)
        assertEquals(1, result.size)
        assertEquals("Kotlin", result[0].language)
    }
}
