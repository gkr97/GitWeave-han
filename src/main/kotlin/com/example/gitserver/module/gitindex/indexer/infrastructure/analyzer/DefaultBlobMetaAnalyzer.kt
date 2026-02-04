package com.example.gitserver.module.gitindex.indexer.infrastructure.analyzer

import com.example.gitserver.common.extension.countLines
import com.example.gitserver.common.extension.detectMimeType
import com.example.gitserver.common.extension.isBinaryFile
import com.example.gitserver.module.gitindex.shared.domain.policy.BlobMetaAnalyzer
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class DefaultBlobMetaAnalyzer : BlobMetaAnalyzer {

    /**
     * 작은 파일(5MB 이하) 분석
     * - MIME 타입 감지
     * - 바이너리 여부 판단
     * - 라인 수 계산
     */
    override fun analyzeSmall(all: ByteArray): Triple<String?, Boolean, Int?> {
        val mime = all.detectMimeType()
        val isBinary = all.isBinaryFile()
        val lineCount = if (!isBinary) all.countLines() else null
        return Triple(mime, isBinary, lineCount)
    }

    /**
     * 큰 파일(5MB 초과) 분석
     * - MIME 타입 감지
     * - 바이너리 여부 판단
     * - 라인 수 계산은 수행하지 않음
     */
    override fun analyzeLarge(ins: InputStream, size: Long, sampleBytes: Int): Triple<String?, Boolean, Int?> {
        val sample = ins.readNBytes(sampleBytes)
        val mime = sample.detectMimeType()
        val isBinary = sample.isBinaryFile()
        return Triple(mime, isBinary, null)
    }
}